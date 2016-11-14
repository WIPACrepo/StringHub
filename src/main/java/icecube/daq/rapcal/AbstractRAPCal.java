package icecube.daq.rapcal;

import icecube.daq.dor.TimeCalib;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.util.TimeUnits;
import icecube.daq.util.UTC;

import java.util.Iterator;
import java.util.LinkedList;

import org.apache.log4j.Logger;

/**
 * Provides the base RAPCal implementation.
 *
 * Maintains a list of Isochron instances that span a continuous period
 * of time.
 *
 * Each Isochron is defined by a pair of sequential time calibrations which
 * define the relationship between the DOM, DOR and UTC clocks for an
 * interval of time.
 *
 * For a particular DOM clock value, the isochron that contains it is considered
 * the best source for UTC time reconstruction.  For DOM clock values that do
 * not have a bounding isochron, the nearest isochron is used for time
 * reconstruction by extrapolating the frequency variation of its interval.
 * Clients are advised to implement strategies that maximize the use of
 * bounding tcals. Note that this may be unavoidable for data generated prior
 * to the initial tcal, or for data acquired after the final time calibration.
 *
 * This class is designed for subclasses to define a fine time correction
 * strategy utilizing the RAPCal waveforms.
 *
 *
 * Design Notes:
 *
 * This class is not thread safe. Multi-threaded clients may wrap instances
 * with SynchronizedRAPCal to support multi-threaded use.
 *
 * Updates are infrequent in relation to dom clock reconstructions. Defensive
 * and optimization calculations are preferred at the point of update.
 *
 */
public abstract class AbstractRAPCal implements RAPCal
{

    private static final Logger  logger =
            Logger.getLogger(AbstractRAPCal.class);

    /**
     * Name of property that controls the weight of each sample in the
     * tcal cable lengthe average.
     */
    public static final String PROP_EXP_WEIGHT =
            "icecube.daq.rapcal.AbstractRAPCal.expWeight";

    /**
     * Name of property that controls the number of isochrons to
     * keep in the lookback history
     */
    public static final String PROP_HISTORY =
            "icecube.daq.rapcal.AbstractRAPCal.history";

    /**
     * Name of property that controls the threshold at which a sample
     * is determined to be an outlier.
     */
    public static final String PROP_WILD_TCAL_THRESH =
            "icecube.daq.rapcal.AbstractRAPCal.wildTcalThresh";

    /**
     * The number of waveform sample used to define the baseline.
     */
    private static final int BASELINE_SAMPLES = 20;

    /**
     * The number of consecutive stable samples required to establish
     * the average cable length.
     */
    public static final int REQUIRED_SETUP_SAMPLES = 3;

    /**
     * The maximum amount that a sample's cable length may vary from
     * the average before being discarded.
     */
    private static final double WILD_TCAL_THRESHOLD = 1.0E-09 *
            Double.parseDouble(System.getProperty(PROP_WILD_TCAL_THRESH, "10")
            );

    /** ID of RAPCAL source */
    private long mbid;

    /** List of Isochrons, ordered sequentially. */
    private final LinkedList<Isochron> hist;

    /** A direct reference to the head element of the isochron list. */
    private Isochron latestIsochron;

    /** The number of isochrons to maintain in the list. */
    private final int            maxHistory;

    /** The span of DOM time currently in history. */
    private long lowerBound = Long.MAX_VALUE;
    private long upperBound = Long.MIN_VALUE;

    /** The most recent time calibration. */
    private TimeCalib            lastTcal;

    /** Weighted average (exponential) of cable length measurements */
    private final ExponentialAverage clenAverage;

    /**
     * Provides notification of anomalous conditions if a monitor has
     * been set.
     */
    private static class MoniGuard
    {
        IRunMonitor runMonitor;
        void push(long mbid, Isochron isochron)
        {
            if(runMonitor != null)
            {
                runMonitor.push(mbid, isochron);
            }
        }
        void push(long mbid, double cableLength, double averageLen)
        {
            if(runMonitor != null)
            {
                runMonitor.pushWildTCal(mbid, cableLength, averageLen);
            }
        }
        void push(long mbid, final BadTCalException exception,
                  final TimeCalib tcal)
        {
            if(runMonitor != null)
            {
                runMonitor.pushException(mbid, exception, tcal);
            }
        }
        void push(long mbid, final RAPCalException exception)
        {
            if(runMonitor != null)
            {
                runMonitor.pushException(mbid, exception, null);
            }
        }
    }
    private final MoniGuard moniGuard = new MoniGuard();

    /** Defines the initialization states. */
    enum State
    {
        NoTCAL,      // must wait for a tcal
        OneTCAL,     // must wait until a successful isochron initialization
        Initialized, // At least one isochron has been established
    }
    private State state = State.NoTCAL;


    /**
     * Construct an instance with parameters provided by environment.
     */
    public AbstractRAPCal()
    {
        this(Double.parseDouble(System.getProperty(PROP_EXP_WEIGHT, "0.1")),
                Integer.getInteger(PROP_HISTORY, 20));
    }

    /**
     * Construct rapcal base class with specific weight and history
     * parameters.
     *
     * @param w exponential averaging weight [0:+inf]
     * @param maxHistory The number of isochrons to maintain in
     *                   the lookback history.
     */
    public AbstractRAPCal(double w, int maxHistory)
    {
        this.clenAverage = new ExponentialAverage(w,
                WILD_TCAL_THRESHOLD, REQUIRED_SETUP_SAMPLES);
        lastTcal = null;
        this.maxHistory = maxHistory;
        hist = new LinkedList<Isochron>();
    }


    @Override
    public double cableLength()
    {
        return clenAverage.getAverage();
    }

    @Override
    public double epsilon()
    {
        if(isReady())
        {
            return hist.getLast().getEpsilon();
        }
        else
        {
            return Double.NaN;
        }
    }

    @Override
    public boolean isReady()
    {
        // we are ready once we have at least one Isochron.
        return hist.size() > 0;
    }


    /**
     * Ask if RAPCal data is available later in time than the DOM clock
     * value provided.  This information may be used to make an informed
     * decision about whether or not to perform a RAPCal in the present
     * or perhaps to hold the data until the RAPCal state is updated so
     * that extrapolation into the future is not required.
     */
    public boolean laterThan(long domclk)
    {
        // This method is called per-hit so we optimize away the
        // list lookup and use the head reference.
        if(latestIsochron != null)
        {
            return latestIsochron.laterThan(domclk, TimeUnits.DOM);
        }
        else
        {
            return false;
        }
    }


    /**
     * Translate dom clock space hits to UTC space (global time = seconds since
     * 00:00:00.0000000 Jan 1 of the current year).  Requires at least two
     * valid TimeCalib packets to have been registered with this service.
     * <i>Caveat utilitor</i>: the DOM clock drift is such that the linearized
     * time tranformation is valid for only a couple of seconds around the
     * TimeCalib packet data.  This service attempts to keep a recent history
     * of the transformations however it is possible for a not particularly
     * relevant transformation to be applied if the TimeCalibs are not up
     * to date.
     *
     * @return UTC global time or null if the transformation could not be
     *         applied.
     *
     */
    public UTC domToUTC(long domclk)
    {
        return domToUTC(domclk, domclk);
    }

    /**
     * Same as @see domToUTC but use another clock for lookup purposes
     * (mainly for debugging, I guess).
     *
     * @param domclk - the clock you want to transform
     * @param atclk - the clock which should be used to search out the RAPCal
     * @return globally-translated time in UTC units.
     */
    public UTC domToUTC(long domclk, long atclk)
    {
        //
        // Iterate thru list until you find (A) bracketing Isochron, or (B) end of list.
        //
        Isochron iso = lookupIsochron(atclk, TimeUnits.DOM);
        if(iso != null)
        {
            return new UTC(iso.reconstructUTC(domclk, TimeUnits.DOM));
        }
        else
        {
            return null;
        }
    }

    /**
     * Search the history for the isochron that should be used to reconstruct
     * a particular time. Tries to find a bounding isochron, but will select
     * the nearest isochron for cases where extrapolation is unavoidable.
     *
     * This method is extracted to support testing.
     *
     * @param atclk The DOM time to search for.
     * @return The Isochron which bounds the time, or the last isochron in
     *         the history if a bounding isochron is not present in the
     *         history, or null if there is no history.
     */
    Isochron lookupIsochron(long atclk, TimeUnits units)
    {
        // Note: Optimized for the case that the most recent
        //       (head of list) isochron bounds the time. Then
        //       search backward.
        if(latestIsochron != null &&
                latestIsochron.containsDomClock(atclk, units))
        {
            return latestIsochron;
        }

        Iterator<Isochron> reverse = hist.descendingIterator();
        while(reverse.hasNext())
        {
            Isochron iso = reverse.next();
            if (iso.containsDomClock(atclk, units))
            {
                return iso;
            }
        }

        // time is not contained in the history
        // choose the head/tail isocron that is nearest
        if(hist.size() > 0)
        {
            //choose the nearest isochron
            if(units.asUTC(atclk) > upperBound)
            {
                return hist.getLast();
            }
            else if(units.asUTC(atclk) <= lowerBound)
            {
                return hist.getFirst();

            }
            else
            {
                //this is a coding error or corruption
                throw new Error("Gap in isochron history:" +
                        " interval [" + lowerBound + "," + upperBound + "]" +
                        " value [" + units.asUTC(atclk) + "]" );
            }
        }
        else
        {
            return null;
        }
    }

    /**
     * Update the RAPCal with a new time calibration.
     *
     * @param tcal The time calibration measurement.
     * @param gpsOffset The offset of the DOR clock with respect to UTC
     *                  time.
     * @return <tt>false</tt> if the update failed
     * @throws RAPCalException A degenerate condition exist in the time
     *                         calibration data.
     */
    public boolean update(TimeCalib tcal, UTC gpsOffset) throws RAPCalException
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("RAPCal update - history size is " + hist.size());
        }

        try {

            boolean rtnval;
            switch (state)
            {
                case NoTCAL:
                    state = State.OneTCAL;
                    rtnval = true;
                    break;
                case OneTCAL:
                    rtnval = addIsochron(new Isochron(setupVierling(lastTcal),
                                     setupVierling(tcal), gpsOffset.in_0_1ns()));
                    state = State.Initialized;
                    break;
                case Initialized:
                    rtnval = addIsochron(new Isochron(hist.getLast(),
                                     setupVierling(tcal), gpsOffset.in_0_1ns()));
                    break;
                default:
                    throw new Error("Unknown state");
            }

            return rtnval;
        }
        catch(BadTCalException bte)
        {
            moniGuard.push(mbid, bte, tcal);
            throw bte;
        }
        catch(RAPCalException re)
        {
            moniGuard.push(mbid, re);
            throw re;
        }
        finally
        {
            // Note: In order to recover from a string of degenerate tcals
            //       at initialization, we must always increment the last
            //       tcal to the most recent;
            lastTcal = tcal;
        }
    }

    /**
     * Add an Isochron to the history, enforcing the following invariants.
     *
     * Samples with an unstable cable length are rejected.
     * Isochron history must be contiguous and free from gaps.
     *
     * @param isochron The Isochron to add.
     * @return <tt>false</tt> if the isochron was not added
     */
    private boolean addIsochron(final Isochron isochron)
    {
        // if we're monitoring a run, send every isochron to the monitor
        moniGuard.push(mbid, isochron);

        boolean withinThreshold = clenAverage.add(isochron.getCableLength());

        // An Isochron that varies from the average is rejected as
        // a "Wild TCAL"
        if(!withinThreshold)
        {
            moniGuard.push(mbid, isochron.getCableLength(),
                           clenAverage.getAverage());
            return false;
        }


        int size = hist.size();
        if (size > 0)
        {
            // ensure that isochrons are contiguous
            if(hist.getLast().getUpperBound() != isochron.getLowerBound())
            {
                //this is a coding error or corruption, isochrons build
                // on one another
                throw new Error("Gap in isochron [" +
                        hist.getLast().getUpperBound() + ", "
                        + isochron.getLowerBound() + "]");
            }
            if (size == maxHistory) hist.removeFirst();

        }
        hist.add(isochron);
        latestIsochron = isochron;

        lowerBound=hist.getLast().getLowerBound();
        upperBound=hist.getLast().getUpperBound();

        return true;
    }

    /**
     * Applies the fine time corrections to a time calibration.
     * @param tcal The time calibration.
     * @return The time calibration time points, with adjustments applied.
     * @throws RAPCalException The RAPCal waveform was degenerate.
     */
    private long[] setupVierling(TimeCalib tcal) throws RAPCalException
    {
        long[] t = new long[4];
        final double dorCorrectionSeconds =
                getFineTimeCorrection(tcal.getDorWaveform());
        final double domCorrectionSeconds =
                getFineTimeCorrection(tcal.getDomWaveform());
        t[0] = tcal.getDorTx().in_0_1ns();
        t[3] = tcal.getDorRx().in_0_1ns() +
                (long) (1.0e+10 * dorCorrectionSeconds);
        t[1] = tcal.getDomRx().in_0_1ns() +
                (long) (1.0e+10 * domCorrectionSeconds);
        t[2] = tcal.getDomTx().in_0_1ns();
        return t;
    }

    /**
     * Provides fine time corrections to the time calibration utilizing the
     * waveform.  Specialized implementations provided by subclasses.
     *
     * @param w The RAPCal waveform.
     * @return The fine time correction to apply to the time calibration,
     *         in seconds.
     * @throws RAPCalException Indicates a degenerate rapcal waveform.
     */
    abstract double getFineTimeCorrection(short[] w) throws RAPCalException;

    /**
     * Provide basic baseline estimator service for derived classes.
     * @param w RAPCal waveform
     * @return mean baseline computed from head of RAPCal waveform
     */
    protected double getBaseline(short[] w)
    {
        double baseline = 0.0;
        for (int i = 0; i < BASELINE_SAMPLES; i++) baseline += w[i];
        return baseline / BASELINE_SAMPLES;
    }

    /**
     * Set this DOM's mainboard ID
     *
     * @param mbid mainboard ID
     */
    public void setMainboardID(long mbid)
    {
        this.mbid = mbid;
    }

    /**
     * Set the run monitoring object.
     *
     * @param runMonitor The run monitoring object
     */
    public void setRunMonitor(IRunMonitor runMonitor)
    {
        moniGuard.runMonitor = runMonitor;
    }
}

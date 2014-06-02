package icecube.daq.rapcal;

import icecube.daq.dor.TimeCalib;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.util.UTC;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.log4j.Logger;

public abstract class AbstractRAPCal implements RAPCal
{
    public static final String PROP_EXP_WEIGHT =
    "icecube.daq.rapcal.AbstractRAPCal.expWeight";
    public static final String PROP_HISTORY =
        "icecube.daq.rapcal.AbstractRAPCal.history";
    public static final String PROP_WILD_TCAL_THRESH =
        "icecube.daq.rapcal.AbstractRAPCal.wildTcalThresh";

    private static final int BASELINE_SAMPLES = 20;

    class Isochron
    {
        private UTC[] t0, t1;
        private UTC gpsOffset;
        private double ratio;
        private double epsilon;
        private long domMid;
        private long dorMid;
        private double clen;
        private final double wildTcalThresh = 1.0E-09 * Double.parseDouble(
			System.getProperty(PROP_WILD_TCAL_THRESH, "10")
		);

        Isochron(TimeCalib tcal0, TimeCalib tcal1, UTC gpsOffset) throws RAPCalException
        {
            this(setupVierling(tcal0), tcal1, gpsOffset);
        }

        Isochron(Isochron prev, TimeCalib tcal, UTC gpsOffset) throws RAPCalException
        {
            this(prev.t1, tcal, gpsOffset);
        }

        Isochron(UTC[] utc, TimeCalib tcal, UTC gpsOffset) throws RAPCalException
        {
            this.gpsOffset = gpsOffset;

            t0 = utc;
            t1 = setupVierling(tcal);
            String errmsg = proc();
            if (errmsg != null) {
                logger.warn(errmsg);
                moni.send(errmsg, tcal);
            }
        }

        /**
         * Check whether give DOM oscillator time is between bounding TCALs
         * @param domclk dom oscillator time in 25 ns ticks
         * @return true if this Isochron spans that time
         */
        boolean containsDomClock(long domclk)
        {
            // convert domclk to 0.1 ns units
            UTC domclkUtc = new UTC(domclk * 250L);
            return (domclkUtc.compareTo(t0[2]) > 0) && (domclkUtc.compareTo(t1[2]) <= 0);
        }

        private String proc()
        {
            String errmsg = null;

            long dor_dt = UTC.add(t1[0], t1[3]).subtractAsUTC(UTC.add(t0[0], t0[3])).in_0_1ns() / 2L;
            long dom_dt = UTC.add(t1[1], t1[2]).subtractAsUTC(UTC.add(t0[1], t0[2])).in_0_1ns() / 2L;
            epsilon = (double) (dor_dt - dom_dt) / dom_dt;
            // Note that using double precision here but DOM internal delay is small number so OK
            clen  = 0.5 * (UTC.subtract(t1[3], t1[0]) - (1.0+epsilon) * UTC.subtract(t1[2], t1[1]));
            if (Double.isNaN(clenAvg))
                clenAvg = clen;
            else if (Math.abs(clenAvg - clen) < wildTcalThresh)
            {
                clenAvg = (clenAvg + expWt * clen) / (1.0 + expWt);
            }
            else
            {
                // wild TCAL!
                errmsg = "Wild TCAL - cable len: " + clen +
                    " avg. cable len: " + clenAvg;
            }
            if (logger.isDebugEnabled())
            {
                logger.debug("\n" +
                        " t0: " + t0[0] + ", " + t0[1] + ", " + t0[2] + ", " + t0[3] + "\n" +
                        " t1: " + t1[0] + ", " + t1[1] + ", " + t1[2] + ", " + t1[3] + "\n" +
                        String.format(" Epsilon: %.3f ppb cable dT: %.1f ns",
                                1.0E+09*epsilon, 1.0E+09*clen)
                        );
            }

	    // proc gets called a limited number of times
	    // domToUTC can be called many many times
	    // ( on the order of 2-3 x per hit ), move
	    // these calculations as they never change
	    domMid = UTC.add(t1[1], t1[2]).in_0_1ns() / 2L;
            dorMid = UTC.add(t1[0], t1[3]).in_0_1ns() / 2L;

            return errmsg;
        }

        UTC domToUTC(long domclk)
        {
            long dt = 250L*domclk - domMid;
            // Correct for DOM frequency variation
            dt += (long) (epsilon * dt);
            if (logger.isDebugEnabled())
            {
                logger.debug("Translating DOM time " + domclk + " at distance " +
                        dt / 10L + " ns from isomark.");
            }
            return UTC.add(gpsOffset, new UTC(dorMid + dt));
        }

        public String toString()
        {
            return "Isochron[" + t0 + "," + t1 + ",epsilon=" + epsilon +
                ",domMid=" + domMid + ",dorMid=" + dorMid + ",clen=" + clen +
                "]";
        }
    }

    private LinkedList<Isochron> hist;
    private TimeCalib            lastTcal;

    /** Weighted average (exponential) of cable length measurements */
    private double               clenAvg;
    private final double         expWt;
    private final int            MAX_HISTORY;
    private static final Logger  logger = Logger.getLogger(AbstractRAPCal.class);
    private LiveTCalMoni moni;

    public AbstractRAPCal()
    {
        this(Double.parseDouble(System.getProperty(PROP_EXP_WEIGHT, "0.1")));
    }

	/**
	 * Construct rapcal base class with weight parameter
	 *
	 * @param w exponential averaging weight [0:+inf]
	 */
	public AbstractRAPCal(double w)
	{
		this.expWt = w;
		lastTcal = null;
		clenAvg = Double.NaN;
		hist = new LinkedList<Isochron>();
		MAX_HISTORY = Integer.getInteger(PROP_HISTORY, 10);
	}

	public double getAverageCableLength()
	{
	    return cableLength();
	}

	public double getLastCableLength()
	{
		if (hist.isEmpty()) return 0.0;
		return hist.getLast().clen;
	}

	public void update(TimeCalib tcal, UTC gpsOffset) throws RAPCalException
	{
	    if (logger.isDebugEnabled())
	    {
	        logger.debug("RAPCal update - history size is " + hist.size());
	    }

            try {
                if (hist.size() > 0)
                {
                    Isochron prev = hist.getLast();
                    if (hist.size() > MAX_HISTORY) hist.removeFirst();
                    hist.add(new Isochron(prev, tcal, gpsOffset));
                }
                else if (lastTcal != null)
                {
                    hist.add(new Isochron(lastTcal, tcal, gpsOffset));
                }
            } catch (RAPCalException re) {
                StringBuffer msgbuf = new StringBuffer(re.getSource());
                msgbuf.append(':');
                short[] waveform = re.getWaveform();
                for (int i = 0; i < waveform.length; i++) {
                    msgbuf.append(" ").append(waveform[i]);
                }

                moni.send(msgbuf.toString(), tcal);

                // rethrow the exception so callers can do something with it
                throw re;
            }

	    lastTcal = tcal;
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
	    if (hist.isEmpty()) return false;
	    final UTC domclkUtc = new UTC(250L * domclk);
	    Isochron iso = hist.getLast();
	    if (iso.t1[2].compareTo(domclkUtc) >= 0) return true;
	    return false;
	}

	public double cableLength() { return clenAvg; }

	/**
	 * Get the ratio of the DOR clock to the DOM clock running frequencies.
	 * @return fDOR / fDOM * 2.  The factor of two is inserted since the
	 * DOR nominally runs at 20 MHz while the DOM clock nominally runs at
	 * 40 MHz so the returned number should be something very close to 1.000000...
	 */
	public double clockRatio()
	{
	    if (hist.isEmpty()) return Double.NaN;
	    Isochron iso = hist.getLast();
	    return iso.ratio;
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
	 * @return UTC global time or null if the transformation could not be applied.
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
	    /*
	     * Iterate thru list until you find (A) bracketing Isochron, or (B) end of list.
             */
	    Isochron last = null;
	    for (Isochron iso : hist)
	    {
	        if (iso.containsDomClock(atclk)) {
	            return iso.domToUTC(domclk);
	        }
	        last = iso;
	    }
            if (last == null) {
                return null;
            }
	    return last.domToUTC(domclk);
	}

	abstract double getFineTimeCorrection(short[] w) throws RAPCalException;

    private UTC[] setupVierling(TimeCalib tcal) throws RAPCalException
    {
        UTC[] t = new UTC[4];
        t[0] = tcal.getDorTx();
        t[1] = UTC.add(tcal.getDomRx(), getFineTimeCorrection(tcal.getDomWaveform()));
        t[2] = tcal.getDomTx();
        t[3] = UTC.add(tcal.getDorRx(), getFineTimeCorrection(tcal.getDorWaveform()));
        return t;
    }

	/**
	 * Provide basic baseline estimator service for derived classes.
	 * @param w RAPCal waveform
	 * @return mean baseline computed from head of RAPCal waveform
	 */
	protected static double getBaseline(short[] w)
	{
	    double baseline = 0.0;
	    for (int i = 0; i < BASELINE_SAMPLES; i++) baseline += w[i];
	    return baseline / BASELINE_SAMPLES;
	}

    public void setMoni(LiveTCalMoni moni)
    {
        this.moni = moni;
    }
}

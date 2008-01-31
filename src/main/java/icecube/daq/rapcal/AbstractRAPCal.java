package icecube.daq.rapcal;

import java.util.LinkedList;
import java.util.ListIterator;

import org.apache.log4j.Logger;

import icecube.daq.dor.TimeCalib;
import icecube.daq.util.UTC;

public abstract class AbstractRAPCal implements RAPCal 
{
    class Isochron
    {
        private UTC[] t0, t1;
        private UTC gpsOffset;
        private double ratio;
        private double clen;
        
        Isochron(TimeCalib tcal0, UTC gps0, TimeCalib tcal1, UTC gps1)
        {
            t0 = new UTC[4];
            t1 = new UTC[4];
            proc();
            this.gpsOffset = gps1;
        }

        Isochron(Isochron prev, TimeCalib tcal, UTC gpsOffset) throws RAPCalException
        {
            t0 = prev.t1;
            t1 = new UTC[4];
            t1[0] = tcal.getDorTx();
            t1[1] = UTC.add(tcal.getDomRx(), getFineTimeCorrection(tcal.getDomWaveform()));
            t1[2] = tcal.getDomTx();
            t1[3] = UTC.add(tcal.getDorRx(), getFineTimeCorrection(tcal.getDorWaveform()));
            proc();
            this.gpsOffset = gpsOffset;
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
        
        private void proc()
        {
            double dor_dt = UTC.subtract(t1[0], t0[0]);
            double dom_dt = UTC.subtract(t1[1], t0[1]);
            ratio = dor_dt / dom_dt;
            clen  = 0.5 * (UTC.subtract(t1[3], t1[0]) - ratio * UTC.subtract(t1[2], t1[1]));
            if (clenAvg == Double.NaN)
                clenAvg = clen;
            else if (Math.abs(clenAvg - clen) < 100)
            {
                clenAvg = (clenAvg + expWt * clen) / (1.0 + expWt);
            }
            else
            {
                // wild TCAL!
                logger.warn("Wild TCAL - clen: " + clen + " clenAvg: " + clenAvg);
            }
            if (logger.isDebugEnabled())
            {
                logger.debug(String.format("Ratio-1: %.4f ppm cable dT: %.1f ns", ratio - 1.0, clen));
            }
        }
        
        UTC domToUTC(long domclk)
        {
            UTC domClockUtc = new UTC(250L*domclk);
            return UTC.add(gpsOffset, UTC.add(t1[0], ratio*UTC.subtract(domClockUtc, t1[1]) + clenAvg));
        }
    }
    
    private LinkedList<Isochron> hist;
    private TimeCalib            lastTcal;

    /** Weighted average (exponential) of cable length measurements */
    private double               clenAvg;
    private final double         expWt;

    private UTC                  lastGpsOffset;
    private static final Logger  logger = Logger.getLogger(AbstractRAPCal.class);
	

    public AbstractRAPCal()
    {
        this(0.1);
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
	}
	
	public void update(TimeCalib tcal, UTC gpsOffset) throws RAPCalException 
	{		
	    if (hist.size() > 0)
	    {
	        Isochron prev = hist.getLast();
	        hist.add(new Isochron(prev, tcal, gpsOffset));
	    }
	    else if (lastTcal != null && lastGpsOffset != null) 
	    {
	        hist.add(new Isochron(lastTcal, lastGpsOffset, tcal, gpsOffset));
	    }
        lastTcal = tcal;
        lastGpsOffset = gpsOffset;
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
	    Isochron iso = hist.getLast();
	    if (iso == null) return Double.NaN;
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
	    /*
	     * Iterate thru list until you find (A) bracketing Isochron, or (B) end of list.
	     * Since hits are coming in time-ordered you know it is safe to delete old
	     * elements in the list - hence the it.remove() line.
	     */
	    ListIterator<Isochron> it = hist.listIterator();
	    while (it.hasNext())
	    {
	        Isochron iso = it.next();
	        if (iso.containsDomClock(domclk) || it.hasNext() == false) 
	            return iso.domToUTC(domclk);
	        it.remove();
	    }
	    return null;
	}
	
	abstract double getFineTimeCorrection(short[] w) throws RAPCalException;
	
}

package icecube.daq.rapcal;

import org.apache.log4j.Logger;

import icecube.daq.dor.TimeCalib;
import icecube.daq.util.UTC;

public class LeadingEdgeRAPCal implements RAPCal {
	
	UTC[] 	t0, t1;
	double 	ratio;
	double 	clen;
	double 	threshold;
	UTC 	gpsOffset;
	private static final Logger logger = Logger.getLogger(LeadingEdgeRAPCal.class);
	
	public LeadingEdgeRAPCal(double threshold) {
		t0 = null;
		t1 = null;
		this.threshold = threshold;
		gpsOffset = new UTC();
	}
	
	public void update(TimeCalib tcal, UTC gpsOffset) throws RAPCalException {
		
		// These are pushed to the front in case the LECorrection subprocess
		// throws a RAPCal exception - then t0 and t1 are left as before.
		UTC t11 = UTC.add(tcal.getDomRx(), getLECorrection(tcal.getDomWaveform()));
		UTC t13 = UTC.add(tcal.getDorRx(), getLECorrection(tcal.getDorWaveform()));
		t0 = t1;
		t1 = new UTC[4];
		t1[0] = tcal.getDorTx();
		t1[1] = t11;
		t1[2] = tcal.getDomTx();
		t1[3] = t13;
		
		logger.debug("" + t1[0] + " " + t1[1] + " " + t1[2] + " " + t1[3]);
		
		if (t0 != null) {
			double dor_dt = UTC.subtract(t1[0], t0[0]);
			double dom_dt = UTC.subtract(t1[1], t0[1]);
			ratio = dor_dt / dom_dt;
			clen  = 0.5 * (UTC.subtract(t1[3], t1[0]) - ratio * UTC.subtract(t1[2], t1[1]));
		}
		
		logger.debug(String.format("Clock ratio - 1: %.2e -- cable length [ns]: %.1f", 
								  ratio - 1.0, clen * 1.0e+09));
		// Update the GPS offset (this could potentially be static)
		this.gpsOffset = gpsOffset;
		
	}
	
	public double cableLength() {
		return clen;
	}

	public double clockRatio() {
		return ratio;
	}
	
	public UTC domToUTC(long domclk) {
		if (t1 == null) return null;
		return UTC.add(gpsOffset, 
					   UTC.add(t1[0], 
							   ratio * UTC.subtract(new UTC(250L * domclk), 
													t1[1]) + clen)
					   );
	}
	
	/**
	 * Calculate fine time correction to TCAL rough timestamp by using interpolated
	 * leading edge crossing of a threshold. 
	 * @param w input RAPCal waveform
	 * @return leading edge correction to the RAPCal waveform in units of seconds.
	 * @throws RAPCalException when something goes wrong with the edge detection
	 */
	private double getLECorrection(short[] w) throws RAPCalException {
		
		// compute mean of leading samples
		double mean = 0.0;
		for (int i = 0; i < 10; i++) mean += w[i];
		mean /= 10.0;
		
		// look for edge crossing
		for (int i = 10; i < 47; i++) {
			double a = w[i] - mean;
			double b = w[i+1] - mean;
			if (a < threshold && b >= threshold) 
				return 50.0e-09 * (i + (threshold-a) / (b-a) - 48.0);
		}
		
		StringBuffer txt = new StringBuffer();
		txt.append("Past the end of TCAL vector:");
		for (int i = 0; i < 48; i++) txt.append(" " + w[i]);
		logger.warn(txt);
		throw new RAPCalException(w);
	}
}

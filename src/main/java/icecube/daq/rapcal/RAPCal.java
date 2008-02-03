package icecube.daq.rapcal;

import icecube.daq.dor.TimeCalib;
import icecube.daq.util.UTC;

public interface RAPCal {

	public double clockRatio();
	
	public double cableLength();
	
	/**
	 * Test whether rapcal service is ready to 
	 * translate the provided time.
	 * @param domclk
	 * @return
	 */
	public boolean ready(long domclk);
	
	public UTC domToUTC(long domclk);
	
	public void update(TimeCalib tcal, UTC gpsOffset) throws RAPCalException;
	
}

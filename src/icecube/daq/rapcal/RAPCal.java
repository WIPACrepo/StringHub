package icecube.daq.rapcal;

import icecube.daq.dor.TimeCalib;
import icecube.daq.util.UTC;

public interface RAPCal {

	public double clockRatio();
	public double cableLength();
	public UTC domToUTC(long domclk);
	public void update(TimeCalib tcal, UTC gpsOffset) throws RAPCalException;
	
}

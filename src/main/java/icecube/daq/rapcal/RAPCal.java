package icecube.daq.rapcal;

import icecube.daq.dor.TimeCalib;
import icecube.daq.util.UTC;

public interface RAPCal {

	double clockRatio();

	double cableLength();

	/**
	 * Test whether rapcal service is ready to
	 * translate the provided time.
	 * @param domclk
	 * @return
	 */
	boolean laterThan(long domclk);

	UTC domToUTC(long domclk);

	void update(TimeCalib tcal, UTC gpsOffset) throws RAPCalException;

}

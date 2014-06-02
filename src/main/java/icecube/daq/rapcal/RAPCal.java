package icecube.daq.rapcal;

import icecube.daq.dor.TimeCalib;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.util.UTC;

public interface RAPCal {

	double clockRatio();

	double cableLength();

	UTC domToUTC(long domclk);

	/**
	 * Test whether rapcal service is ready to
	 * translate the provided time.
	 * @param domclk
	 * @return
	 */
	boolean laterThan(long domclk);

	void setMoni(LiveTCalMoni moni);

	void update(TimeCalib tcal, UTC gpsOffset) throws RAPCalException;
}

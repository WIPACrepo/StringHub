package icecube.daq.rapcal;

import icecube.daq.dor.TimeCalib;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.util.UTC;

public interface RAPCal
{


    /**
     * The cable length as determined from the time calibrations.
     * @return The cable length, or Double.NaN if the rapcal has not been
     * initialized.
     */
    double cableLength();

    /**
     * The skew between DOM and DOR clock frequency as determined
     * from the time calibrations.
     * @return The latest frequency skew, or Double.NaN if the rapcal has not
     * been initialized.
     */
    double epsilon();

    /**
     * Is rapcal ready to reconstruct dom times.
     *
     * @return True once RAPCal has processed enough tcal measurements to
     *         construct the initial Isochron.
     */
    boolean isReady();

    /**
     * Test whether rapcal service is ready to translate the provided time.
     *
     * Used to prevent or control extrapolation when reconstructing
     * utc timestamps.
     *
     * @param domclk The DOM time to check.
     * @return True if the rapcal has been updated with a time calibration
     *         taken at or later than the dom clock;
     */
    boolean laterThan(long domclk);

    /**
     * Reconstruct the UTC time corresponding to a DOM clock time.
     *
     * @param domclk A timestamp from the DOM clock.
     * @return The corresponding UTC time or null if the rapcal has not
     *         been initialized;
     */
    UTC domToUTC(long domclk);

    /**
     * Update the rapcal with a time calibration measurment.
     *
     * @param tcal The time calibration measurement.
     * @param gpsOffset The offset of the DOR clock with respect to UTC
     *                  time.
     * @return <tt>false</tt> if the update failed
     * @throws RAPCalException An issue occurred during the update. This is
     *                         a recoverable condition in general. Common
     *                         issues are malformed tcal data or tcal data
     *                         that varies unacceptably from the average.
     */
    boolean update(TimeCalib tcal, UTC gpsOffset) throws RAPCalException;

    /**
     * Set the mainboard ID for this rapcal.
     */
    void setMainboardID(long mbid);

    /**
     * Register a monitor to receive run-related events from the rapcal.
     *
     * @param runMonitor The monitor to register.
     */
    void setRunMonitor(IRunMonitor runMonitor);
}

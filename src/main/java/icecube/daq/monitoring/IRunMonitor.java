package icecube.daq.monitoring;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.TimeCalib;
import icecube.daq.rapcal.Isochron;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.util.DeployedDOM;

import java.util.Collection;

public interface IRunMonitor
{
    /**
     * Is the thread running?
     *
     * @return <tt>true</tt> if the thread is running
     */
    boolean isRunning();

    /**
     * If the thread is running, wait for it to die.
     *
     * @throws InterruptedException if the join was interrupted
     */
    void join()
        throws InterruptedException;

    void push(long mbid, Isochron isochron);

    void pushException(int string, int card, GPSException exception);

    void pushException(long mbid, RAPCalException exc, TimeCalib tcal);

    void pushGPSMisalignment(int string, int card, GPSInfo oldGPS,
                             GPSInfo newGPS);

    void pushGPSProcfileNotReady(int string, int card);

    void pushWildTCal(long mbid, double cableLength, double averageLen);

    /**
     * Set the list of DOMs configured for this string
     *
     * @param configuredDOMs list of configured DOMs
     */
    void setConfiguredDOMs(Collection<DeployedDOM> configuredDOMs);

    void setRunNumber(int runNumber);

    /**
     * Start the thread.
     *
     * @throws Error if the thread is already running
     */
    void start();

    /**
     * Notify the thread that it should stop.
     */
    void stop();
}

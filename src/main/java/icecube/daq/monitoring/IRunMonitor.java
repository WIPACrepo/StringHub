package icecube.daq.monitoring;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.payload.IUTCTime;
import icecube.daq.rapcal.Isochron;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.util.DOMInfo;

import java.util.Collection;
import java.util.Map;

public interface IRunMonitor
{
    /**
     * Add some hits the total number of HLC hits for this period.
     * @param domID An array of mainboard IDs
     * @param utc An array of utc times at which an hlc hit occurred for
     *            the dom in the corresponding slot of the domID array.
     */
    void countHLCHit(long[] domID, long[] utc);

    /**
     * Return the list of DOMs configured for this string
     *
     * @return map of mainboard ID -&gt; deployed DOM data
     */
    Iterable<DOMInfo> getConfiguredDOMs();

    /**
     * Get DOM information
     *
     * @param mbid DOM mainboard ID
     *
     * @return dom information
     */
    DOMInfo getDom(long mbid);

    /**
     * Get the string representation of the starting time for this run
     *
     * @return starting time
     */
    String getStartTimeString();

    /**
     * Get the string representation of the ending time for this run
     *
     * @return ending time
     */
    String getStopTimeString();

    /**
     * Get this string's number
     *
     * @return string number
     */
    int getString();

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

    /**
     * Push isochron data onto the consumer's queue
     *
     * @param mbid DOM mainboard ID
     * @param isochron isochron
     */
    void push(long mbid, Isochron isochron);

    /**
     * Push GPS exception data onto the consumer's queue
     *
     * @param string string number
     * @param card card number
     * @param exception GPS exception
     */
    void pushException(int string, int card, GPSException exception);

    /**
     * Push RAPCal exception data onto the consumer's queue
     *
     * @param mbid DOM mainboard ID
     * @param exc RAPCal exception
     * @param tcal time calibration data which caused this exception
     */
    void pushException(long mbid, RAPCalException exc, TimeCalib tcal);

    /**
     * Push GPS misalignment data onto the consumer's queue
     *
     * @param card card number
     * @param oldGPS previous GPS information
     * @param newGPS new, problematic GPS information
     */
    void pushGPSMisalignment(int string, int card, GPSInfo oldGPS,
                             GPSInfo newGPS);

    /**
     * Push GPS procfile error data onto the consumer's queue
     *
     * @param string string number
     * @param card card number
     */
    void pushGPSProcfileNotReady(int string, int card);

    /**
     * Push wild TCal error data onto the consumer's queue
     *
     * @param mbid DOM mainboard ID
     * @param cableLength bad cable length
     * @param averageLen average cable length
     */
    void pushWildTCal(long mbid, double cableLength, double averageLen);

    /**
     * Send monitoring message to Live
     *
     * @param varname quantity name
     * @param priority message priority
     * @param utc pDAQ UTC timestamp
     * @param map field-&gt;value map
     * @param addString if <tt>true</tt>, add "string" entry to map
     */
    void sendMoni(String varname, Alerter.Priority priority, IUTCTime utc,
                  Map<String, Object> map, boolean addString);

    /**
     * Set the list of DOMs configured for this string
     *
     * @param configuredDOMs list of configured DOMs
     */
    void setConfiguredDOMs(Collection<DOMInfo> configuredDOMs);

    /**
     * Set the run number
     *
     * @param runNumber new run number
     */
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

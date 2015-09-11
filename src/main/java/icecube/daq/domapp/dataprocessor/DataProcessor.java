package icecube.daq.domapp.dataprocessor;

import icecube.daq.domapp.RunLevel;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.util.UTC;

import java.nio.ByteBuffer;


/**
 * Defines the interface into the data processor.
 *
 * The DataCollector offloads processing through this interface.
 *
 * Although synchronous, single threaded operational modes are available,
 * this interface must support asynchronous behavior.
 */
public interface DataProcessor
{

    /**
     * The SLC / delta-compressed hit buffer magic number.
     */
    final static int MAGIC_ENGINEERING_HIT_FMTID = 2;
    final static int MAGIC_COMPRESSED_HIT_FMTID  = 3;
    final static int MAGIC_MONITOR_FMTID         = 102;
    final static int MAGIC_TCAL_FMTID            = 202;
    final static int MAGIC_SUPERNOVA_FMTID       = 302;


    /**
     * Identifies the data streams
     */
    enum StreamType
    {
        HIT,
        SUPERNOVA,
        MONI,
        TCAL
    }

    /**
     * Defines the interface to the sub-processors which handle
     * a specific stream type.
     */
    interface StreamProcessor
    {
        void process(ByteBuffer data, DataStats counters)
                throws DataProcessorError;
        void eos() throws DataProcessorError;
        void runLevel(RunLevel runLevel);
    }


    /**
     * Provides an interface for accessing counts of data that has
     * been  processed.
     *
     * @return The counter object.
     */
    DataStats getDataCounters();

    /**
     * Notifies processor of state changes. This is required mainly
     * to inform tcal dispatching about transitions to and from the
     * RUNNING state.
     *
     * Take note that this notification is with respect to the data being
     * processed, therefore it must be called by the acquiring thread when it
     * processes a run level transition. (As opposed to the controller
     * signaling a run level change on the controller thread.) As a
     * consequence, the processor is only notified of these run levels:
     * IDLE, CONFIGURED, RUNNING
     * Flasher runs also notify these run levels:
     * STARTING_SUBRUN, CONFIGURING
     *
     * @param runLevel The run level that the DataCollector has transitioned
     *                 to.
     */
    void runLevel(RunLevel runLevel) throws DataProcessorError;

    /**
     * Send data for processing on a specified stream.
     *
     * @param stream Identifies the type of data.
     * @param data The data in domapp payload format.
     * @throws DataProcessorError
     */
    void process(StreamType stream, ByteBuffer data) throws DataProcessorError;


    /**
     * Send an EOS on a specified stream.
     * @param stream The stream to send eos to.
     * @throws DataProcessorError
     */
    void eos(StreamType stream) throws DataProcessorError;


    /**
     * Send an EOS on all streams.
     * The processor will continue to run.
     *
     * @throws DataProcessorError
     */
    void eos() throws DataProcessorError;

    /**
     * Shutdown the processor.
     *
     * Queued processing will be completed.
     * EOS makers will be send on all open channels.
     *
     * @throws DataProcessorError
     */
    void shutdown() throws DataProcessorError;

    /**
     * Enable a client to synchronize with the data processing queue.
     *
     * Caller will block until processing queue is fully serviced.
     *
     * @throws DataProcessorError
     */
    void sync() throws DataProcessorError;


    /**
     * Resolve a domclock time to UTC time using the rapcal
     * instance owned by the processor.
     *
     * Use with caution from the acquisition thread because it
     * entails a wait for all queued processing to complete before
     * returning.
     *
     * Expected usage is to establish UTC times for run transitions like
     * start run and start subrun.
     *
     * @param domclock A domclock timestamp.
     * @return The reconstructed UTC time corresponding to the
     * DOM clock.
     */
    UTC resolveUTCTime(final long domclock) throws DataProcessorError;

    /**
     * Provide the live alert service to the rapcal instance
     */

    public void setLiveMoni(LiveTCalMoni moni) throws DataProcessorError;


}





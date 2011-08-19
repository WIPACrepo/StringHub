package icecube.daq.domapp;

import icecube.daq.dor.DOMChannelInfo;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

/**
 * DOM data collector factory.
 */
public interface IDataCollectorFactory
{
    /**
     * Create a DataCollector using the specified DOM channel and attach it
     * to the specified output channel.
     *
     * @param chInfo DOM channel info
     * @param chan output channel
     *
     * @return new Data Collector
     *
     * @throws IOException if there was a problem initializing I/O
     * @throws MessageException if DataCollector could not be created
     */
    AbstractDataCollector create(DOMChannelInfo chInfo, 
        WritableByteChannel chan)
        throws IOException, MessageException;

    /**
     * The richer version of creation function for DataCollector objects.
     * This factory function will allow creation of a DataCollector, real or
     * simulated, which provides all 4 data channels.
     * @param chInfo
     * @param hitChannel
     * @param moniChannel
     * @param snChannel
     * @param tcalChannel
     * @return
     */
    AbstractDataCollector create(DOMChannelInfo chInfo, 
        WritableByteChannel hitChannel, WritableByteChannel moniChannel,
        WritableByteChannel snChannel, WritableByteChannel tcalChannel)
        throws IOException, MessageException;
    /**
     * Reset the factory.
     */
    void reset();
}

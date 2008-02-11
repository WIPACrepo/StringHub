package icecube.daq.domapp;



import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.rapcal.LeadingEdgeRAPCal;
import icecube.daq.rapcal.RAPCal;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

import org.apache.log4j.Logger;

/**
 * Create string hub data collectors.
 */
public class DataCollectorFactory
    implements IDataCollectorFactory
{
    private static final Logger LOGGER =
        Logger.getLogger(DataCollectorFactory.class);

    /**
     * Create a DataCollector using the specified DOM channel, and attach
     * it to the specified output channel.
     *
     * @param chInfo DOM channel info
     * @param chan output channel
     *
     * @return new Data Collector
     *
     * @throws IOException if there was a problem initializing I/O
     * @throws MessageException if DataCollector could not be created
     */
    public AbstractDataCollector create(DOMChannelInfo chInfo,
                                WritableByteChannel chan)
        throws IOException, MessageException
    {
        IDOMApp app = new DOMApp(chInfo.card, chInfo.pair, chInfo.dom);
        RAPCal rapcal = new LeadingEdgeRAPCal();

        DataCollector dc;
        try {
            dc = new DataCollector(chInfo.card, chInfo.pair, chInfo.dom,
                    null, chan, null, null, null,
                    Driver.getInstance(), rapcal, app);
        } catch (MessageException me) {
            LOGGER.fatal("Couldn't create data collector for dom " +
                         chInfo.mbid, me);
            System.exit(1);
            return null;
        }

        return dc;
    }

    public AbstractDataCollector create(DOMChannelInfo chInfo, WritableByteChannel hitChannel,
    		WritableByteChannel moniChannel, WritableByteChannel snChannel,
    		WritableByteChannel tcalChannel)
    throws IOException, MessageException
	{
    	// TODO not currently supported.
    	return null;
	}

    /**
     * Reset the factory.
     */
    public void reset()
    {
        // nothing to be done
    }
}

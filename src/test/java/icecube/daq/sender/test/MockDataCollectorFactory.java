package icecube.daq.sender.test;


import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.IDataCollectorFactory;
import icecube.daq.domapp.MessageException;
import icecube.daq.dor.DOMChannelInfo;

import java.io.IOException;

import java.nio.channels.WritableByteChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * Create string hub data collectors with connections to mock objects.
 */
public class MockDataCollectorFactory
    implements IDataCollectorFactory
{
    private double simulationTime;
    private double rate;

    private ArrayList sources = new ArrayList();

    /**
     * Create a MockDataCollector.
     *
     * @param simulationTime number of simulated seconds
     * @param rate rate at which hits are generated 
     */
    MockDataCollectorFactory(double simulationTime, double rate)
    {
        this.simulationTime = simulationTime;
        this.rate = rate;
    }

    /**
     * Create a DataCollector using the specified DOM channel, and attach it
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
    public AbstractDataCollector create(DOMChannelInfo chInfo,
                                        WritableByteChannel chan)
        throws IOException, MessageException
    {
        return create(chInfo, chan, null, null, null);
    }

    public AbstractDataCollector create(DOMChannelInfo chInfo,
                                        WritableByteChannel hitChan,
                                        WritableByteChannel moniChan,
                                        WritableByteChannel snChan,
                                        WritableByteChannel tcalChan)
        throws IOException, MessageException
    {
        long mbId;
        try {
            mbId = Long.parseLong(chInfo.mbid, 16);
        } catch (NumberFormatException nfe) {
            throw new Error("Bad mainboard ID \"" + chInfo.mbid + "\"");
        }

        MockDOMApp da =
            new MockDOMApp(mbId, simulationTime, rate);

        DataCollector dc
            = new DataCollector(chInfo.card, chInfo.pair, chInfo.dom,
                                hitChan, moniChan, snChan, tcalChan,
                                new MockDriver(), new MockRAPCal(), da);

        sources.add(da);

        return dc;
    }

    /**
     * Get the list of input sources.
     */
    List getSources()
    {
        return sources;
    }

    /**
     * Reset the factory.
     */
    public void reset()
    {
        sources.clear();
    }
}

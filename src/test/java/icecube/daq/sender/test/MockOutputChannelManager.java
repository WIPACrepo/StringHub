package icecube.daq.sender.test;

import icecube.daq.io.DAQOutputChannelManager;
import icecube.daq.io.OutputChannel;

/**
 *
 */
public class MockOutputChannelManager implements DAQOutputChannelManager
{
    private final OutputChannel chan;

    public MockOutputChannelManager(OutputChannel chan)
    {
        this.chan = chan;
    }

    public OutputChannel getChannel()
    {
        return chan;
    }
}

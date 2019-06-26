package icecube.daq.sender;

import icecube.daq.io.DAQOutputChannelManager;
import icecube.daq.io.OutputChannel;

import java.nio.ByteBuffer;

/**
 * Wrapper around a DAQOutputChannelManager that advertises
 * the OutputChannel interface (likely before the managed output
 * channel is even available).
 *
 * On first use, the target OutputChannel will be resolved and
 * calls will be forwarded to it.
 *
 * This class is useful for moving OutputChannel members into
 * object construction rather than implementing late binding
 * setter methods.
 *
 * The implementation assumes that the final target OutputChannel
 * will be plumbed and available before the first channel calls
 * occur.
 */
public class OutputChannelFuture implements OutputChannel
{
    private OutputChannel delegate;

    public OutputChannelFuture()
    {
        this.delegate = new Unplumbed();
    }

    public void setChannelManager(final DAQOutputChannelManager manager)
    {
        this.delegate = new ChannelManagerFuture(manager);
    }

    @Override
    public void receiveByteBuffer(final ByteBuffer buf)
    {
        delegate.receiveByteBuffer(buf);
    }

    @Override
    public void sendLastAndStop()
    {
        delegate.sendLastAndStop();
    }

    private void setTarget(OutputChannel target)
    {
        this.delegate = target;
    }

    /**
     * The initial-state target that generates errors on usage.
     */
    private static class Unplumbed implements OutputChannel
    {
        @Override
        public void receiveByteBuffer(final ByteBuffer buf)
        {
                // indicates premature use.
                throw new Error("Channel not available");
        }

        @Override
        public void sendLastAndStop()
        {
            // indicates premature use.
            throw new Error("Channel not available");
        }
    }

    /**
     * An initial-state target that queries the channel manager
     * for the target and swaps it in if available.
     */
    class ChannelManagerFuture implements OutputChannel
    {
        final DAQOutputChannelManager future;

        ChannelManagerFuture(final DAQOutputChannelManager future)
        {
            this.future = future;
        }

        @Override
        public void receiveByteBuffer(final ByteBuffer buf)
        {
            OutputChannel target = future.getChannel();
            if (target != null)
            {
                OutputChannelFuture.this.setTarget(target);
                target.receiveByteBuffer(buf);
            }
            else
            {
                // indicates premature use.
                throw new Error("Channel not available");
            }
        }

        @Override
        public void sendLastAndStop()
        {
            OutputChannel target = future.getChannel();
            if (target != null)
            {
                OutputChannelFuture.this.setTarget(target);
                target.sendLastAndStop();
            }
            else
            {
                // indicates premature use.
                throw new Error("Channel not available");
            }
        }
    }


}

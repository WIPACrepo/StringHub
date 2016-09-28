package icecube.daq.performance.diagnostic;

import icecube.daq.sender.Sender;

/**
 * Provides trace content derived from a Sender instance.
 */
public class SenderContent implements Content
{
    private final Sender sender;
    private final String header;

    public SenderContent(final Sender sender)
    {
        this.sender = sender;
        this.header = String.format("%-10s %-10s", "queued", "readouts");

    }

    @Override
    public void header(final StringBuilder sb)
    {
        sb.append(header);
    }

    @Override
    public void content(final StringBuilder sb)
    {
        int queued = sender.getNumHitsQueued();
        long readouts = sender.getNumReadoutsSent();
        sb.append(String.format("%-10d %-10d", queued, readouts));
    }

}

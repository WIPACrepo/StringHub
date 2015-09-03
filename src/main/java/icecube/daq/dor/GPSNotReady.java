package icecube.daq.dor;

/**
 * Thrown when a read from the gpssync file returns less than
 * 22 bytes which indicates that there are no buffered snaps available.
 */
public class GPSNotReady extends GPSException
{
    public GPSNotReady()
    {
        super();
    }

    public GPSNotReady(final String message)
    {
        super(message);
    }

    public GPSNotReady(final String message, final Throwable cause)
    {
        super(message, cause);
    }

    public GPSNotReady(final Throwable cause)
    {
        super(cause);
    }
}

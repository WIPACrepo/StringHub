package icecube.daq.dor;

/**
 * Indicates a problem reading from the gpssync file.
 */
public class GPSException extends Exception {

    public GPSException()
    {
        super();
    }

    public GPSException(final String message)
    {
        super(message);
    }

    public GPSException(final String message, final Throwable cause)
    {
        super(message, cause);
    }

    public GPSException(final Throwable cause)
    {
        super(cause);
    }
}

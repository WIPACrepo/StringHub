package icecube.daq.time.gps;

/**
 * Indicates a failure of the GPS service.
 */
public class GPSServiceError extends Exception
{

    public GPSServiceError()
    {
        super();
    }

    public GPSServiceError(final String message)
    {
        super(message);
    }

    public GPSServiceError(final String message, final Throwable cause)
    {
        super(message, cause);
    }

    public GPSServiceError(final Throwable cause)
    {
        super(cause);
    }
}

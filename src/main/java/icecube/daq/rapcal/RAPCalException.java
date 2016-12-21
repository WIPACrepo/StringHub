package icecube.daq.rapcal;

/**
 * Thrown when RAPCal encounters an error.
 */
public class RAPCalException extends Exception {

    public RAPCalException()
    {
        super();
    }

    public RAPCalException(final String message)
    {
        super(message);
    }

    public RAPCalException(final String message, final Throwable cause)
    {
        super(message, cause);
    }

    public RAPCalException(final Throwable cause)
    {
        super(cause);
    }
}

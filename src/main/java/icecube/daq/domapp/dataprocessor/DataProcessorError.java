package icecube.daq.domapp.dataprocessor;

/**
 * General error class for exceptions encountered during
 * data processing.
 */
public class DataProcessorError extends Exception
{
    public DataProcessorError(final String message)
    {
        super(message);
    }

    public DataProcessorError(final String message, final Throwable cause)
    {
        super(message, cause);
    }

    public DataProcessorError(final Throwable cause)
    {
        super(cause);
    }
}

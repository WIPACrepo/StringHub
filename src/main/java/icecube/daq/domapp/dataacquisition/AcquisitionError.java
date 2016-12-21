package icecube.daq.domapp.dataacquisition;

/**
 * General error class for exceptions encountered during
 * data acquisition.
 */
public class AcquisitionError extends Exception
{

    public AcquisitionError()
    {
    }

    public AcquisitionError(final String message)
    {
        super(message);
    }

    public AcquisitionError(final String message, final Throwable cause)
    {
        super(message, cause);
    }

    public AcquisitionError(final Throwable cause)
    {
        super(cause);
    }
}

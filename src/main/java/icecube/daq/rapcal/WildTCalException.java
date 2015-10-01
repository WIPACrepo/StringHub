package icecube.daq.rapcal;

/**
 * A typed exception to isolate wild tcals.
 */
public class WildTCalException extends RAPCalException
{
    public WildTCalException(final String message)
    {
        super(message);
    }
}

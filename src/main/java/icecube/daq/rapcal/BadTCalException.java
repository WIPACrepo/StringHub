package icecube.daq.rapcal;

/**
 * Thrown when rapcal encounters a bad tcal.
 *
 * The general case occurs when the fine time correction fails to find a
 * reference feature int the tcal waveform.
 */
public class BadTCalException extends RAPCalException
{

    private static final long serialVersionUID = 1L;
    private final String source;
    private final short[] waveform;

    public BadTCalException(String source, short[] waveform) {
        super(source);
        this.source = source;
        this.waveform = waveform;
    }

    public String getSource() { return source; }
    public short[] getWaveform() { return waveform; }
}

package icecube.daq.rapcal;

public class RAPCalException extends Exception {

	private static final long serialVersionUID = 1L;
	private String source;
	private short[] waveform;

	public RAPCalException(String source, short[] waveform) {
		this.source = source;
		this.waveform = waveform;
	}

	public String getSource() { return source; }
	public short[] getWaveform() { return waveform; }

}

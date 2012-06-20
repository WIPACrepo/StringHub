package icecube.daq.rapcal;

public class RAPCalException extends Exception {

	private static final long serialVersionUID = 1L;
	private short[] waveform;

	public RAPCalException(short[] waveform) {
		this.waveform = waveform;
	}

	public short[] getWaveform() { return waveform; }

}

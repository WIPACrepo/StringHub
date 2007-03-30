package icecube.daq.domapp;

/**
 * A class which represents the Local Coincidence mode
 * setting - that is - whether or not the DOM firmware
 * pays attention to the LC signal from above, below,
 * both, or neither.
 * @author kael
 */
public enum LCMode {
	OFF(0), UPDOWN(1), UP(2), DOWN(3);
	private byte mode;
	
	LCMode(int mode) { this.mode = (byte) mode; }
	byte getValue() { return mode; }
}

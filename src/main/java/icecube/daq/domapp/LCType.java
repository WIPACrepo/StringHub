package icecube.daq.domapp;

public enum LCType {
	SOFT_LC(1), HARD_LC(2), FLABBY_LC(3);
	private byte mode;
	LCType(int mode) { this.mode = (byte) mode; }
	byte getValue() { return mode; }
}
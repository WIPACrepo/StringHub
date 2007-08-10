package icecube.daq.domapp;

public enum TriggerMode {
	TEST_PATTERN(0), FORCED(1),	SPE(2),	FB(3), MPE(4);
	private byte mode;
	TriggerMode(int mode) {
		this.mode = (byte) mode;
	}
	byte getValue() { return mode; }
}

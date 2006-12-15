package icecube.daq.domapp;

import static org.junit.Assert.*;

import org.junit.Test;

public class EngineeringRecordFormatTest {

	@Test
	public void testERFmtConstructX1() throws BadEngineeringFormat {
		short atwd[] = { 128, 64, 64, 0 };
		EngineeringRecordFormat fmt = new EngineeringRecordFormat((short) 200, atwd);
		byte enc[] = fmt.encode();
		assertEquals(-56, (int) enc[0]);
		assertEquals(127, (int) enc[1]);
		assertEquals(7, (int) enc[2]);
	}
	
	@Test
	public void testERFmtConstructEncoded() {
		EngineeringRecordFormat fmt = new EngineeringRecordFormat((byte) -200, (byte) 3, (byte) 0);
		assertEquals((short) 56, fmt.fadcSamples());
		assertEquals((short) 32, fmt.atwdSamples(0));
		assertEquals((short) 0,  fmt.atwdSamples(1));
		assertEquals((short) 0,  fmt.atwdSamples(2));
		assertEquals((short) 0,  fmt.atwdSamples(3));
		for (int ch = 0; ch < 4; ch++) 
			assertEquals((short) 2, fmt.atwdWordsize(ch));
	}
	
	@Test
	public void testByteShift() {
		byte a0 = 3;
		byte a1 = -1;
		short word = (short) ((a0 << 8) | a1);
		assertEquals((short) 0x3ff, word);
	}
	
	@Test
	public void testDownCast() {
		int largeInt = 500;
		byte a = (byte) largeInt;
		System.out.println(largeInt + " -> " + a);
		int b = (int) a;
		int c = b & 0xff;
		System.out.println(c);
	}

}

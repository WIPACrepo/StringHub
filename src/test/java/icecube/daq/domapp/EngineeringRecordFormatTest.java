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
		EngineeringRecordFormat fmt = new EngineeringRecordFormat((byte) 180, (byte) 0x53, (byte) 0);
		assertEquals((short) 180, fmt.fadcSamples());
		assertEquals((short) 32, fmt.atwdSamples(0));
        assertEquals((short) 2, fmt.atwdWordsize(0));
		assertEquals((short) 64, fmt.atwdSamples(1));
        assertEquals((short) 1, fmt.atwdWordsize(1));
		assertEquals((short) 0,  fmt.atwdSamples(2));
        assertEquals((short) 1, fmt.atwdWordsize(2));
		assertEquals((short) 0,  fmt.atwdSamples(3));
        assertEquals((short) 1, fmt.atwdWordsize(3));
	}

}

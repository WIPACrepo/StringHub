package icecube.daq.domapp;

import java.nio.ByteBuffer;

public class MonitorRecordFactory {

	public static MonitorRecord createFromBuffer(ByteBuffer buf)
	{
		int pos  = buf.position();
		int recl = buf.getShort(pos);
		int mrid = buf.getShort(pos+2);
		switch (mrid)
		{
		case 0xCB: // ASCII monitor log messages
			return new AsciiMonitorRecord(buf);
		case 0xC8: // Hardware monitor record
			return new HardwareMonitorRecord(buf);
		default:   // Unknown record
			return new MonitorRecord(buf);
		}
	}
}

package icecube.daq.domapp;

import java.nio.ByteBuffer;

public class MonitorRecordFactory 
{
    public static MonitorRecord createFromBuffer(ByteBuffer buf)
    {
        int pos  = buf.position();
        int mrid = buf.getShort(pos + 2);
        switch (mrid) {
        case 0xCB:
            return new AsciiMonitorRecord(buf);
        case 0xC8:
            return new HardwareMonitorRecord(buf);
        default:  
            return new MonitorRecord(buf);
        }
    }
}

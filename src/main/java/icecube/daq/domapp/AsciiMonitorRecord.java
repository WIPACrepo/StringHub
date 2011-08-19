package icecube.daq.domapp;

import java.nio.ByteBuffer;

public class AsciiMonitorRecord extends MonitorRecord
{
    private String text;

    public AsciiMonitorRecord(ByteBuffer buf)
    {
        // call the superclass constructor to handle clock &c.
        super(buf);
        // ASCII text string is payload beyond 10-byte header
        int len = getLength() - 10;
        byte[] str = new byte[len];
        record.position(10);
        record.get(str);
        text = new String(str);
        record.rewind();
    }

    public String toString() 
    {
        return text;
    }
}

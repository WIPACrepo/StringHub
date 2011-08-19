package icecube.daq.domapp;

import java.nio.ByteBuffer;

/**
 * Base monitor record class.
 * @author krokodil
 */
public class MonitorRecord 
{

    protected short recl;
    protected short fmtId;
    protected long domclk;
    protected ByteBuffer record;

    public MonitorRecord(ByteBuffer buf)
    {
        int pos = buf.position();
        int limit = buf.limit();
        recl = buf.getShort(pos);
        fmtId = buf.getShort(pos + 2);
        domclk = DOMAppUtil.decodeClock6B(buf, pos + 4);
        record = ByteBuffer.allocate(recl);
        buf.limit(pos + recl);
        record.put(buf).flip();
        buf.limit(limit);
    }

    public int getLength() 
    { 
        return recl; 
    }
    public int getType() 
    { 
        return fmtId;
    }
    public long getClock() 
    { 
        return domclk; 
    }
    public ByteBuffer getBuffer() 
    { 
        return (ByteBuffer) record; 
    }

}

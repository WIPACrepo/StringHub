package icecube.daq.stringhub.test;

import icecube.daq.payload.IDOMID;
import icecube.daq.payload.IReadoutRequestElement;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.IWriteablePayloadRecord;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MockReadoutRequestElement
    implements IReadoutRequestElement, IWriteablePayloadRecord
{
    private int type;
    private IUTCTime firstTime;
    private IUTCTime lastTime;
    private IDOMID domId;
    private ISourceID srcId;

    public MockReadoutRequestElement(int type, long firstTime, long lastTime,
                                     long domId, int srcId)
    {
        this(type, new MockUTCTime(firstTime), new MockUTCTime(lastTime),
             new MockDOMID(domId), new MockSourceID(srcId));
    }

    public MockReadoutRequestElement(int type, IUTCTime firstTime,
                                     IUTCTime lastTime, IDOMID domId,
                                     ISourceID srcId)
    {
        this.type = type;
        this.firstTime = firstTime;
        this.lastTime = lastTime;
        this.domId = domId;
        this.srcId = srcId;
    }

    @Override
    public Object deepCopy()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public IDOMID getDOMID()
    {
        return domId;
    }

    @Override
    public long getFirstTime()
    {
        if (firstTime == null) {
            return 0L;
        }

        return firstTime.longValue();
    }

    @Override
    public IUTCTime getFirstTimeUTC()
    {
        return firstTime;
    }

    @Override
    public long getLastTime()
    {
        if (lastTime == null) {
            return 0L;
        }

        return lastTime.longValue();
    }

    @Override
    public IUTCTime getLastTimeUTC()
    {
        return lastTime;
    }

    @Override
    public int getReadoutType()
    {
        return type;
    }

    @Override
    public ISourceID getSourceID()
    {
        return srcId;
    }

    /**
     * Determines if this record is loaded with valid data.
     * @return <tt>true</tt> if data is loaded, <tt>false</tt> otherwise.
     */
    @Override
    public boolean isDataLoaded()
    {
        throw new Error("Unimplemented");
    }

    /**
     * Loads the data from the buffer into the container record.
     *
     * @param offset the offset into the byte buffer
     * @param buffer ByteBuffer from which to construct the record.
     */
    @Override
    public void loadData(int offset, ByteBuffer buffer)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Write this element to the byte buffer
     * @param buf byte buffer
     * @param offset index of first byte
     */
    @Override
    public void put(ByteBuffer buf, int offset)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Write this record to the bute buffer.
     *
     * @param iOffset the offset at which to start writing the object.
     * @param tBuffer the ByteBuffer into which to write this payload-record
.
     * @return the number of bytes written to this byte buffer.
     */
    @Override
    public int writeData(int iOffset, ByteBuffer tBuffer)
        throws IOException
    {
        throw new Error("Unimplemented");
    }

    @Override
    public String toString()
    {
        String typeStr;
        switch (type) {
        case READOUT_TYPE_GLOBAL:
            typeStr = "GLOBAL";
            break;
        case READOUT_TYPE_II_GLOBAL:
            typeStr = "II_GLOBAL";
            break;
        case READOUT_TYPE_IT_GLOBAL:
            typeStr = "IT_GLOBAL";
            break;
        case READOUT_TYPE_II_STRING:
            typeStr = "II_STRING";
            break;
        case READOUT_TYPE_II_MODULE:
            typeStr = "II_MODULE";
            break;
        case READOUT_TYPE_IT_MODULE:
            typeStr = "IT_MODULE";
            break;
        default:
            typeStr = "UNKNOWN";
            break;
        }

        return "MockReadoutRequestElement[" + typeStr + " [" + firstTime +
            "-" + lastTime + "] dom " + domId + " src " + srcId + "]";
    }
}

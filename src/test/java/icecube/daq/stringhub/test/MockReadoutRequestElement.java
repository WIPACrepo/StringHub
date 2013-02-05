package icecube.daq.stringhub.test;

import icecube.daq.payload.IDOMID;
import icecube.daq.payload.IPayloadDestination;
import icecube.daq.payload.IReadoutRequestElement;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.IWriteablePayloadRecord;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;

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

    public Object deepCopy()
    {
        throw new Error("Unimplemented");
    }

    public void dispose()
    {
        // do nothing
    }

    public IDOMID getDomID()
    {
        return domId;
    }

    public IUTCTime getFirstTimeUTC()
    {
        return firstTime;
    }

    public IUTCTime getLastTimeUTC()
    {
        return lastTime;
    }

    public int getReadoutType()
    {
        return type;
    }

    public ISourceID getSourceID()
    {
        return srcId;
    }

    /**
     * Determines if this record is loaded with valid data.
     * @return <tt>true</tt> if data is loaded, <tt>false</tt> otherwise.
     */
    public boolean isDataLoaded()
    {
        throw new Error("Unimplemented");
    }

    /**
     * Loads the data from the buffer into the container record.
     *
     * @param offset the offset into the byte buffer
     * @param buffer ByteBuffer from which to construct the record.
     *
     * @exception IOException if errors are detected reading the record
     * @exception DataFormatException if the record is not of the correct format
     */
    public void loadData(int offset, ByteBuffer buffer)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Unimplemented
     * @param buf unused
     * @param offset unused
     */
    public void put(ByteBuffer buf, int offset)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Write this record to the payload destination.
     *
     * @param dest PayloadDestination to which to write this record.
     *
     * @return the number of bytes written to this destination.
     */
    public int writeData(IPayloadDestination dest)
        throws IOException
    {
        throw new Error("Unimplemented");
    }

    /**
     * Write this record to the bute buffer.
     *
     * @param offset the offset at which to start writing the object.
     * @param buffer the ByteBuffer into which to write this payload-record
.
     * @return the number of bytes written to this byte buffer.
     */
    public int writeData(int iOffset, ByteBuffer tBuffer)
        throws IOException
    {
        throw new Error("Unimplemented");
    }

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

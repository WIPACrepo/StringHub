package icecube.daq.bindery;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Comparator;

/**
 * Compare DAQBuffer objects
 */
class DAQBufferComparator
    implements Comparator<DAQBuffer>
{
    public int compare(DAQBuffer left, DAQBuffer right)
    {
        if (left.timestamp < right.timestamp)
            return -1;
        else if (left.timestamp > right.timestamp)
            return 1;
        else
            return 0;
    }
}

/**
 * Encapsulate DOM data for sorting
 */
class DAQBuffer
{
    ByteBuffer buf;
    long mbid;
    long timestamp;
    private String str;

    /**
     * Initialize a DAQBuffer
     *
     * @param buf buffer containing DAQ data
     */
    DAQBuffer(ByteBuffer buf)
    {
        this.buf = buf;

        final ByteOrder order = buf.order();
        buf.order(ByteOrder.BIG_ENDIAN);
        mbid = buf.getLong(8);
        timestamp = buf.getLong(24);
        buf.order(order);
    }

    /**
     * Is this an end-of-stream marker?
     *
     * @return <tt>true</tt> if this is an end-of-stream marker
     */
    public boolean isEOS()
    {
        return timestamp == Long.MAX_VALUE;
    }

    /**
     * Return a string describing the internal state of this object
     *
     * @return debugging string
     */
    public String toString()
    {
        if (str == null) {
            if (isEOS()) {
                str = String.format("EOS@%012x", mbid);
            } else {
                str = String.format("%d@%012x", timestamp, mbid);
            }
        }

        return str;
    }
}

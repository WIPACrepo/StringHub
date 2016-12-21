package icecube.daq.replay;

import icecube.daq.payload.PayloadRegistry;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

/**
 * Get/set UTC time for payloads in ByteBuffers.
 */
final class BBUTC
{
    private static final Logger LOG = Logger.getLogger(BBUTC.class);

    /**
     * Get the time from the buffer
     */
    static long get(ByteBuffer buf)
    {
        final int len = buf.getInt(0);
        if (buf.limit() == 4 && len == 4) {
            // don't change STOP payload
            return 0;
        }

        // buffer must contain a 4-byte length, a 4-byte type, and
        // at least 6 bytes of time data
        if (buf.limit() < 14 || len < 14) {
            throw new Error(String.format("Cannot get time from %d-byte" +
                                          " buffer (%d len)", buf.limit(),
                                          buf.getInt(0)));
        }

        switch (buf.getInt(4)) {
        case PayloadRegistry.PAYLOAD_ID_DELTA_DOMHIT:
            return getLong(buf, 24, 32);
        case PayloadRegistry.PAYLOAD_ID_MON:
            return getLong(buf, 8, 16);
        case PayloadRegistry.PAYLOAD_ID_SN:
            return getLong(buf, 8, 16);
        case PayloadRegistry.PAYLOAD_ID_TCAL:
            return getLong(buf, 8, 16);
        default:
            throw new Error(String.format("Cannot get time for type %d",
                                          buf.getInt(4)));
        }
    }

    /**
     * Get time from the buffer as a long value
     */
    private static long getLong(ByteBuffer buf, int pos, int endpos)
    {
        if (buf.limit() < endpos) {
            throw new Error("Buffer is " + (endpos - buf.limit()) +
                            " bytes too short");
        }

        if (pos + 8 == endpos) {
            return buf.getLong(pos);
        }

        if (pos + 6 != endpos) {
            throw new Error("Cannot retrieve " + (endpos - pos) + " byte time");
        }

        long domClk = 0;
        for (int i = pos; i < endpos; i++) {
            domClk = (domClk << 8) | buf.get(i);
        }

        return domClk;
    }

    /**
     * Set the time in the buffer
     */
    static void set(ByteBuffer buf, long newTime)
    {
        final int len = buf.getInt(0);
        if (buf.limit() == 4 && len == 4) {
            // don't change STOP payload
            return;
        }

        // buffer must contain a 4-byte length, a 4-byte type, and
        // at least 6 bytes of time data
        if (buf.limit() < 14 || len < 14) {
            throw new Error(String.format("Cannot set time in %d-byte" +
                                          " buffer (%d len)", buf.limit(),
                                          len));
        }

        switch (buf.getInt(4)) {
        case PayloadRegistry.PAYLOAD_ID_DELTA_DOMHIT:
            setLong(buf, 24, 32, newTime);
            break;
        case PayloadRegistry.PAYLOAD_ID_MON:
            setLong(buf, 8, 16, newTime);
            break;
        case PayloadRegistry.PAYLOAD_ID_SN:
            setLong(buf, 8, 16, newTime);
            break;
        case PayloadRegistry.PAYLOAD_ID_TCAL:
            setLong(buf, 8, 16, newTime);
            break;
        default:
            throw new Error(String.format("Cannot set time for type %d",
                                          buf.getInt(4)));
        }
    }

    /**
     * Set the hit time
     *
     * @param buf hit buffer
     * @param newTime time to write to hit buffer
     */
    static void setLong(ByteBuffer buf, int pos, int endpos, long newTime)
    {
        if (buf.limit() < endpos) {
            throw new Error("Buffer is " + (endpos - buf.limit()) +
                            " bytes too short");
        }

        if (pos + 8 == endpos) {
            buf.putLong(pos, newTime);
        } else if (pos + 6 == endpos) {
            for (int i = endpos - 1; i >= pos; i--) {
                buf.put(i, (byte)(newTime & 0xff));
                newTime >>= 8;
            }
        } else {
            throw new Error("Cannot retrieve " + (endpos - pos) + " byte time");
        }
    }
}

package icecube.daq.sender.test;


import icecube.daq.bindery.StreamBinder;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.trigger.IReadoutRequestElement;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Append-only byte buffer manager.
 */
class AppendBuffer
{
    /** Managed byte buffer. */
    private ByteBuffer byteBuffer;
    /** Current offset. */
    private int offset;

    /**
     * Create an append-only byte buffer.
     *
     * @param total byte buffer length
     */
    AppendBuffer(int len)
    {
        byteBuffer = ByteBuffer.allocate(len);
    }

    /**
     * Add a 4-byte integer value to the buffer.
     *
     * @param val value
     */
    void addInt(int val)
    {
        byteBuffer.putInt(offset, val);
        offset += 4;
    }

    /**
     * Add an 8-byte integer value to the buffer.
     *
     * @param val value
     */
    void addLong(long val)
    {
        byteBuffer.putLong(offset, val);
        offset += 8;
    }

    /**
     * Add a 2-byte integer value to the buffer.
     *
     * @param val value
     */
    void addShort(int val)
    {
        byteBuffer.putShort(offset, (short) val);
        offset += 2;
    }

    /**
     * Check that the current offset matches the expected value.
     *
     * @param expOffset expected offset
     */
    void checkOffset(int expOffset)
    {
        if (offset != expOffset) {
            throw new IndexOutOfBoundsException("Expected to be at offset " +
                                                expOffset + ", not " + offset);
        }
    }

    /**
     * Get the managed byte buffer.
     *
     * @return byte buffer
     */
    ByteBuffer getBuffer()
    {
        return byteBuffer;
    }

    void reset()
    {
        offset = 0;
    }
}

class RequestGenerator
    extends Thread
{
    private static Log logger = LogFactory.getLog(RequestGenerator.class);

    /**
     * fields with a fixed size (in bytes)
     */
    private static final int PAYLOAD_LENGTH_SIZE = 4;
    private static final int PAYLOAD_TYPE_SIZE   = 4;
    private static final int PAYLOAD_TIME_SIZE   = 8;

    private static final int REQUEST_TYPE_SIZE   = 2;
    private static final int TRIGGER_UID_SIZE    = 4;
    private static final int SOURCE_ID_SIZE      = 4;
    private static final int NUM_ELEMENTS_SIZE   = 4;

    private static final int REQELEM_TYPE_SIZE   = 4;
    private static final int FIRST_UTCTIME_SIZE  = 8;
    private static final int LAST_UTCTIME_SIZE   = 8;
    private static final int REQELEM_DOMID_SIZE  = 8;

    private static final int COMP_LENGTH_SIZE    = 4;
    private static final int COMP_TYPE_SIZE      = 2;
    private static final int COMP_NUMELEM_SIZE   = 2;

    // payload envelope sizes
    private static final int PAYLOAD_ENVELOPE_SIZE =
        PAYLOAD_LENGTH_SIZE +
        PAYLOAD_TYPE_SIZE +
        PAYLOAD_TIME_SIZE;

    // readout request element header sizes
    private static final int REQELEM_HEADER_SIZE =
        REQUEST_TYPE_SIZE +
        TRIGGER_UID_SIZE +
        SOURCE_ID_SIZE +
        NUM_ELEMENTS_SIZE;

    // fixed header for readout request buffer
    private static final int FIXED_HEADER_SIZE =
        PAYLOAD_ENVELOPE_SIZE +
        REQELEM_HEADER_SIZE;

    private static final int REQELEM_SIZE =
        REQELEM_TYPE_SIZE +
        SOURCE_ID_SIZE +
        FIRST_UTCTIME_SIZE +
        LAST_UTCTIME_SIZE +
        REQELEM_DOMID_SIZE;

    // composite payload envelope
    private static final int COMPOSITE_ENVELOPE_SIZE =
        COMP_LENGTH_SIZE +
        COMP_TYPE_SIZE +
        COMP_NUMELEM_SIZE;

    // fixed size of trigger request buffer
    private static final int FIXED_BUFFER_SIZE =
        FIXED_HEADER_SIZE +
        COMPOSITE_ENVELOPE_SIZE;

    private Pipe.SinkChannel sink;
    private int numRequests;
    private int uid;
    private int srcId;
    private double time;
    private double timeStep;
	
    RequestGenerator(Pipe.SinkChannel sink, double maxTime, int numRequests)
    {
        this.sink = sink;
        this.numRequests = numRequests;

        uid = 1;
        srcId = SourceIdRegistry.GLOBAL_TRIGGER_SOURCE_ID;

        time = 0;
        timeStep = maxTime / (double) numRequests;

        setName("RequestGenerator");
    }
	
    public void run()
    {
        for (int i = 0; i < numRequests; i++) {
            try {
                fireEvent();
                logger.debug("fired event - current time is " + time);
            } catch (IOException iox) {
                iox.printStackTrace();
            } catch (InterruptedException intx) {
                intx.printStackTrace();
            }
        }

        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(4);
        buf.flip();

        try {
            int nw = sink.write(buf);
            logger.info("RequestGenerator end-of-stream - " + nw + " bytes.");
        } catch (IOException e) {
            e.printStackTrace();
        }
		
    }
	
    void fireEvent()
        throws IOException, InterruptedException
    {
        double delay = timeStep;

        Thread.sleep((long) (1000.0 * delay));

        final long firstTime = (long) (time*1.0E+10);

        time += timeStep;

        final long lastTime = (long) (time*1.0E+10);

        ByteBuffer buf = generatePayload(uid++, srcId, firstTime, lastTime);

        logger.debug("Delay = " + delay + " time = " + time);
        Thread.sleep((long) (1000.0 * delay));
        int nw = sink.write(buf);
        logger.debug("Wrote " + nw + " bytes.");
    }

    /**
     * Generate a readout request.
     *
     * @return ByteBuffer representation of readout request
     */
    public ByteBuffer generatePayload(int uid, int srcId, long firstTime,
                                      long lastTime)
    {
        final int payloadType = PayloadRegistry.PAYLOAD_ID_READOUT_REQUEST;

        final int readoutLen = 1;

        final int payloadLength = FIXED_BUFFER_SIZE +
            (readoutLen * REQELEM_SIZE);

        AppendBuffer buf = new AppendBuffer(payloadLength);

        // payload envelope
        buf.addInt(payloadLength);
        buf.addInt(payloadType);
        buf.addLong(firstTime);
        buf.checkOffset(PAYLOAD_ENVELOPE_SIZE);

        final int globalRequestType =
            IReadoutRequestElement.READOUT_TYPE_IIIT_GLOBAL;

        // readout request header
        buf.addShort(globalRequestType);
        buf.addInt(uid);
        buf.addInt(srcId);
        buf.addInt(readoutLen);
        buf.checkOffset(FIXED_HEADER_SIZE);

        int expOffset = FIXED_HEADER_SIZE;

        // readout request record
        buf.addInt(globalRequestType);
        buf.addInt(-1);
        buf.addLong(firstTime);
        buf.addLong(lastTime);
        buf.addLong(-1);

        expOffset += REQELEM_SIZE;
        buf.checkOffset(expOffset);

        // empty composite payload header
        buf.addInt(8);
        buf.addShort(1);
        buf.addShort(0);

        // make sure we filled the buffer
        buf.checkOffset(payloadLength);

        // no flip is necessary here since all puts use offsets
        // (position never changes)
        return buf.getBuffer();
    }
}

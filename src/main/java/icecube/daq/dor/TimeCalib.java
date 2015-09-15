package icecube.daq.dor;

import icecube.daq.domapp.DeltaMCodec;
import icecube.daq.util.UTC;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import org.apache.log4j.Logger;

public class TimeCalib {

	private short bytes;
	private short flags;
	private long dorTx, dorRx;
	private short[] dorWaveform;
	private long domRx, domTx;
	private short[] domWaveform;

    /**
     * Transient field that provides a best-effort system
     * monotonic clock reading at the point that the tcal
     * was initiated. Used to correlate the system clock
     * to the DOR clock.
     */
    private long tx_nanos;


    private static final Logger logger = Logger.getLogger(TimeCalib.class);

    public TimeCalib(ByteBuffer buf, long tx_nanos)
    {
        this(buf);
        this.tx_nanos = tx_nanos;
    }

        public TimeCalib(ByteBuffer buf) {
		buf.order(ByteOrder.LITTLE_ENDIAN);
		bytes = buf.getShort();
		flags = buf.getShort();
		dorTx = buf.getLong();
		dorRx = buf.getLong();
		dorWaveform = new short[64];
		for (int i = 0; i < 64; i++) dorWaveform[i] = buf.getShort();
		domRx = buf.getLong();
		domTx = buf.getLong();
		domWaveform = new short[64];
		for (int i = 0; i < 64; i++) domWaveform[i] = buf.getShort();
		if (logger.isDebugEnabled()) logger.debug("Decode TCAL record - len: " + bytes + " - flags: " + flags + " dorTx: " + dorTx);
	}

    /**
     * Add raw values to the map for monitoring
     *
     * @param map map of raw values reported for monitoring
     */
    public void addValues(Map map)
    {
        map.put("DORTX", dorTx);
        map.put("DORRX", dorRx);
        map.put("DORWF", dorWaveform);
        map.put("DOMTX", domTx);
        map.put("DOMRX", domRx);
        map.put("DOMWF", domWaveform);
    }

	/**
	 * Get the DOR TCAL transmit time.
	 * Note the the units are 0.1 ns.
	 * @return UTC object
	 */
	public UTC getDorTx() {
		return new UTC(500L * dorTx);
	}

	/**
	 * Get the DOR TCAL receive time.
	 * @return UTC time (0.1 ns)
	 */
	public UTC getDorRx() {
		return new UTC(500L * dorRx);
	}

	public UTC getDomTx() {
		return new UTC(250L * domTx);
	}

	public UTC getDomRx() {
		return new UTC(250L * domRx);
	}

	public short[] getDorWaveform() {
		return dorWaveform;
	}

	public short[] getDomWaveform() {
		return domWaveform;
	}

    /**
     * Get the DOR TX in DOR clock units.
     * @return the DOR TX.
     */
    public long getDorTxInDorUnits()
    {
        return dorTx;
    }

    /**
     * Get the DOR TX in DOR clock units.
     * @return the DOR RX.
     */
    public long getDorRxInDorUnits()
    {
        return dorRx;
    }

    /**
     * Get the DOM TX in DOR clock units.
     * @return the DOR TX.
     */
    public long getDomTxInDomUnits()
    {
        return domTx;
    }

    /**
     * Get the DOM TX in DOR clock units.
     * @return the DOR RX.
     */
    public long getDomRxInDomUnits()
    {
        return domRx;
    }

    /**
     * Get the monotonic clock point-in-time correlated with the DOR TX time.
     * @return The local monotonic point-in-time of the DOR TX.
     */
    public long getDorTXPointInTimeNano()
    {
        return tx_nanos;
    }

	/**
	 * Write POTC (plain-ol' TCAL) record
	 * to supplier buffer.  Returns length of record
	 * which should be 314.  Note that, conforming to
	 * standard, the TCAL buffer is little-endian.
	 * @param buf receive buffer for data.
	 * @return number of bytes added to buffer
	 */
	public int writeUncompressedRecord(ByteBuffer buf)
	{
		ByteOrder ord = buf.order();
		buf.order(ByteOrder.LITTLE_ENDIAN);
		int pos = buf.position();
		buf.putShort(bytes).putShort(flags);
		buf.putLong(dorTx);
		buf.putLong(dorRx);
		for (int i = 0; i < 64; i++) buf.putShort(dorWaveform[i]);
		buf.putLong(domRx);
		buf.putLong(domTx);
		for (int i = 0; i < 64; i++) buf.putShort(domWaveform[i]);
		buf.order(ord);
		return buf.position() - pos;
	}
	/**
	 * Return a ByteBuffer object with delta 1-2-3-6-11
	 * waveform encoding and the following structure
	 * <pre>
	 *  0 ..  7 DOR Tx - full 8 bytes
	 *  8 ..  9 DOR Rx - DOR Tx - 2 bytes
	 * 10 .. 17 DOM Rx - full 8 bytes
	 * 18 .. 19 DOR Tx - DOM Rx - 2 bytes
	 * 20 ..  M 48 samples of DOR waveform delta compressed
	 * M+1 .. N 48 samples of DOM waveform delta compressed
	 * </pre>
	 * @return number of bytes in the compressed buffer
	 */
	public int writeCompressedBuffer(ByteBuffer buf)
	{
		int pos = buf.position();
		buf.putLong(dorTx).putShort((short) (dorRx - dorTx));
		buf.putLong(domRx).putShort((short) (domTx - domRx));
		DeltaMCodec codec = new DeltaMCodec(buf);
		codec.encode(dorWaveform);
		codec.encode(domWaveform);
		return buf.position() - pos;
	}

}

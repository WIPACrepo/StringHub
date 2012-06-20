package icecube.daq.domapp;

import java.io.Serializable;

public class EngineeringRecordFormat implements Serializable {
	private static final long serialVersionUID = 1L;
	private short fadc_samples;
	private short[] atwd_size;
	private short[] atwd_samples;
	private transient byte[] enc;

	public EngineeringRecordFormat(short fadc_samples, short[] atwd_samples, short[] atwd_size) throws BadEngineeringFormat {

		this.fadc_samples = fadc_samples;
		if (atwd_samples == null) {
			this.atwd_samples = new short[4];
			this.atwd_samples[0] = 128;
			this.atwd_samples[1] = 128;
			this.atwd_samples[2] = 128;
			this.atwd_samples[3] = 0;
		} else {
			this.atwd_samples = atwd_samples;
		}
		if (atwd_size == null) {
			this.atwd_size = new short[4];
			this.atwd_size[0] = 2;
			this.atwd_size[1] = 2;
			this.atwd_size[2] = 2;
			this.atwd_size[3] = 2;
		} else {
			this.atwd_size = atwd_size;
		}

		_int_enc();
	}

	public EngineeringRecordFormat(short fadc_samples, short[] atwd_samples) throws BadEngineeringFormat {
		this(fadc_samples, atwd_samples, null);
	}

	public EngineeringRecordFormat(short fadc_samples) throws BadEngineeringFormat {
		this(fadc_samples, null);
	}

	public EngineeringRecordFormat() {
		this.atwd_samples = new short[4];
		this.atwd_size    = new short[4];
		this.fadc_samples = 255;
		atwd_samples[0] = atwd_samples[1] = atwd_samples[2] = 128;
		atwd_samples[3] = 0;
		atwd_size[0] = atwd_size[1] = atwd_size[2] = atwd_size[3] = 2;
		enc = new byte[3];
		enc[0] = -1;
		enc[1] = -1;
		enc[2] = 15;
	}

	/**
	 * The copy constructor will create a new object initialized with
	 * values from the supplied object
	 * @param copyFrom the object whose values will be copied into the new instance
	 */
	public EngineeringRecordFormat(EngineeringRecordFormat copyFrom)
	{
		fadc_samples = copyFrom.fadc_samples;
		atwd_samples = new short[4];
		atwd_size    = new short[4];
		enc          = new byte[3];
		System.arraycopy(copyFrom.atwd_samples, 0, atwd_samples, 0, 4);
		System.arraycopy(copyFrom.atwd_size, 0, atwd_size, 0, 4);
		System.arraycopy(copyFrom.enc, 0, enc, 0, 3);
	}

	/**
	 * Decode the byte triplet to find engineering format
	 * @param b0 byte #5 of engineering record
	 * @param b1 byte #6 of engineering record
	 * @param b2 byte #7 of engineering record
	 */
	public EngineeringRecordFormat(byte b0, byte b1, byte b2) {

		atwd_samples = new short[4];
		atwd_size = new short[4];

		fadc_samples = b0;
		if (fadc_samples < 0) fadc_samples += 256;

		// decode the nybbles
		int[] n = new int[4];
		n[0] = b1 & 15;
		n[1] = (b1 & 0xf0) >> 4;
		n[2] = b2 & 15;
		n[3] = (b2 & 0xf0) >> 4;
		for (int ch = 0; ch < 4; ch++) {
			if ((n[ch] & 2) != 0)
				atwd_size[ch] = 2;
			else
				atwd_size[ch] = 1;
			if ((n[ch] & 1) != 0) {
				switch ((n[ch] & 12) >> 2) {
				case 0: atwd_samples[ch] = 32; break;
				case 1: atwd_samples[ch] = 64; break;
				case 2: atwd_samples[ch] = 16; break;
				case 3: atwd_samples[ch] = 128; break;
				}
			} else
				atwd_samples[ch] = 0;
		}
	}

	public short fadcSamples() {
		return fadc_samples;
	}

	public void setAtwdSamples(int ch, short val) throws BadEngineeringFormat
	{
		atwd_samples[ch] = val;
		_int_enc();
	}

	public void setAtwdWordsize(int ch, short val) throws BadEngineeringFormat
	{
		atwd_size[ch] = val;
		_int_enc();
	}

	public void setFadcSamples(short val) throws BadEngineeringFormat
	{
		fadc_samples = val;
	}

	public short atwdSamples(int ch) {
		return atwd_samples[ch];
	}

	public short atwdWordsize(int ch) {
		return atwd_size[ch];
	}

	public byte[] encode() {
		return enc;
	}

	private void _int_enc() throws BadEngineeringFormat {
		enc = new byte[3];
		enc[0] = (byte) fadc_samples;
		enc[1] = (byte) (atwdPackedFormat(atwd_size[0], atwd_samples[0]) |
				(atwdPackedFormat(atwd_size[1], atwd_samples[1]) << 4));
		enc[2] = (byte) (atwdPackedFormat(atwd_size[2], atwd_samples[2]) |
				(atwdPackedFormat(atwd_size[3], atwd_samples[3]) << 4));
	}

	private int atwdPackedFormat(int size, int samples) throws BadEngineeringFormat {
		int nybble = 1;
		if (size == 2) nybble = 3;
		switch (samples) {
		case 0: 	return 0;
		case 16: 	return nybble | 8;
		case 32: 	return nybble;
		case 64: 	return nybble | 4;
		case 128: 	return nybble | 12;
		default: 	throw new BadEngineeringFormat(size, samples);
		}
	}

}

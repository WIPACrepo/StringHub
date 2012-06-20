package icecube.daq.domapp;

/**
 * This enum represents the lexicon of messages passed between
 * the StringHub and the DOMApp running on a DOM under its control.
 * The DOMApp messages have a <b>TYPE</b> and <b>SUBTYPE</b>
 * which are identified in the enum constructor.
 * @see John Jacobsen's <a href="http://www.npxdesigns.com/domapp/api.html">DOMApp API</a>
 * @author kael dylan hanson
 *
 */
public enum MessageType {

    /** Request the DOM's 12-character hex mainboard ID */
	GET_DOM_ID(1, 10),
	
	/** Get the DOMApp release information returned as a string */
	GET_DOMAPP_RELEASE(1, 24),
	
	WRITE_DAC(2, 13),
	SET_HV(2, 14),
	ENABLE_HV(2, 16),
	DISABLE_HV(2, 18),
	QUERY_HV(2, 22),
	SET_TRIG_MODE(2, 31),
	MUX_SELECT(2, 35),
	GET_MUX_CH(2, 36),
	SET_PULSER_RATE(2, 37),
	GET_PULSER_RATE(2, 38),
	SET_PULSER_ON(2, 39),
	SET_PULSER_OFF(2, 40),
	SET_SCALER_DEADTIME(2, 43),
	GET_SCALER_DEADTIME(2, 44),
	SET_LC_MODE(2, 45),
	GET_LC_MODE(2, 46),
	SET_LC_WIN(2, 47),
	GET_LC_WIN(2, 48),
	SET_LC_TYPE(2, 49),
	GET_LC_TYPE(2, 50),
	SET_LC_TX(2, 51),
	GET_LC_TX(2, 52),
	SET_LC_SRC(2, 53),
	GET_LC_SRC(2, 54),
	SET_LC_SPAN(2, 55),
	GET_LC_SPAN(2, 56),
	SET_LC_CABLELEN(2, 57),
	GET_LC_CABLELEN(2, 58),
	ENABLE_SN(2, 59),
	DISABLE_SN(2, 60),
	
	/** Set charge stamp type for delta-compressed events. If mode 
	 * is 0 (default), the FADC will be used. If mode is 1, the ATWD
	 * is used. If ATWD, chSel determines whether channel selection 
	 * is auto (0), or specified in the third argument (1). Third 
	 * argument must specify channel 0 or 1.
	 */
	SET_CHARGE_STAMP_TYPE(2, 61),
	
	/** Enable (1) or disable (0) transmission of MinBias waveforms (1:8192 prescale) */
	SELECT_MINBIAS(2,62),
	
	GET_DATA_ACCESS_ERROR_ID(2, 2),
	GET_DATA_ACCESS_ERROR_STR(2, 3),
	GET_DATA(3, 11),
	
	GET_MONI(3, 12),
	SET_MONI_IVAL(3, 13),
	SET_ENG_FORMAT(3, 14),
	GET_FBID(3, 23),
	
	SET_DATA_FORMAT(3, 24),
	GET_DATA_FORMAT(3, 25),
	
	SET_COMPRESSION_MODE(3, 26),
	GET_COMPRESSION_MODE(3, 27),
	
	GET_SN_DATA(3, 28),
	RESET_MONI_BUF(3, 29),
	QUERY_MONI_AVAIL(3, 30),
	QUERY_OVERFLOWS(3, 31),
	
	SET_LBM_DEPTH(3, 32),
	GET_LBM_DEPTH(3, 33),
	
	/**
	 * Generate charge stamp histograms in monitoring stream, at 
	 * <tt>interval</tt> clock ticks (if &ge;40,000,000) or seconds 
	 * (if &lt;40,000,000). If interval is zero (default), no records 
	 * will be generated. Prescale is the divisor of each charge stamp 
	 * amplitude before it is histogrammed. DSC_SET_CHARGE_STAMP_TYPE 
	 * is used to select the type of charge stamp; if FADC (default), 
	 * only the peak value of the charge stamp is histogrammed.  
	 * <p>
	 * The histograms will be generated in the monitoring stream in 
	 * ASCII format, as follows:
	 * <pre>
	 * ATWD CS chip chan--N entries: bin0 bin1 ... bin127
	 * </pre>
	 * (four entries per interval, where chip is A or B and chan is 0 or 1)
	 * or
	 * <pre>
	 * FADC CS--N entries: bin0 bin1 ... bin127
	 * </pre>
	 */
	HISTO_CHARGE_STAMPS(3, 34),
	
	
	SELECT_ATWD(3, 35),
	
	GET_FAST_MONI_RATE_TYPE(3, 36),
	SET_FAST_MONI_RATE_TYPE(3, 37),
	
	/** Start a data collection run on the DOM */
	BEGIN_RUN(4, 12),
	
	/** Stop a data run on the DOM */
	END_RUN(4,13),
	
	/** Execute a pedestal run on the DOM and store pedestals
	 * to be subtracted from waveforms in subsequent data run.
	 */
	COLLECT_PEDESTALS(4, 16),
	
	/** Begin a flasher run */
	BEGIN_FB_RUN(4, 27),
	
	/** Stop an on-going flasher run */
	END_FB_RUN(4, 28),
	
	/** Change FB parameters - akin to the BEGIN_FB_RUN message */
	CHANGE_FB_SETTINGS(4, 29);

	private byte facility;
	private byte subtype;

	MessageType(int facility, int subtype) {
		this.facility = (byte) facility;
		this.subtype  = (byte) subtype;
	}

	public byte getFacility() { return facility; }
	public byte getSubtype() { return subtype; }
	public boolean equals(byte type, byte subtype)
	{
	    return this.facility == type && this.subtype == subtype;
	}

}

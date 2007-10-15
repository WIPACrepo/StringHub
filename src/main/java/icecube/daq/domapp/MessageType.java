package icecube.daq.domapp;

public enum MessageType {
	
	GET_DOM_ID(1, 10),
	GET_DOMAPP_RELEASE(1, 24),
	WRITE_DAC(2, 13),
	SET_HV(2, 14),
	ENABLE_HV(2, 16),
	DISABLE_HV(2, 18),
	QUERY_HV(2, 22),
	SET_TRIG_MODE(2, 31),
	MUX_SELECT(2, 35),
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
	BEGIN_RUN(4, 12),
	END_RUN(4,13),
	COLLECT_PEDESTALS(4, 16),
	BEGIN_FB_RUN(4, 27),
	END_FB_RUN(4, 28);
	
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
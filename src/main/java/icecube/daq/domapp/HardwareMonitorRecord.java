package icecube.daq.domapp;

import java.nio.ByteBuffer;

/**
 * This is a wrapper around the basic MonitorRecord
 * class.  All of the data is stored in the underlying
 * 
 * @author kael
 *
 */
public class HardwareMonitorRecord extends MonitorRecord 
{
	public HardwareMonitorRecord(ByteBuffer buf) 
	{
		super(buf);
	}
	
	public int getRecordVersion() { return record.get(20); }
	
	public short getADC(int ch) { return record.getShort(22 + 2*ch); }
	
	public short getDAC(int ch) { return record.getShort(38 + 2*ch); }
	
	public short getHVSet() { return record.getShort(70); }
	
	public short getHVReadback() { return record.getShort(72); }
	
	/**
	 * 
	 * @return
	 */
	public short getTemperatureADC() { return record.getShort(74); }
	
	/**
	 * Returns the temperature in degrees Celsius
	 * @return the mainboard temperature, in degrees Celsius
	 */
	public float getTemperature() { return getTemperatureADC() / 256.0f; }
	
	/**
	 * Returns the pressure in units of kPa
	 * @return floating-point pressure in kilopascals
	 */
	public float getPressure() { return (getADC(2) / ((float) getADC(0)) + 0.095f) / 0.009f; }

	/**
	 * Get the SPE rate meter counts.  This counter is affected by the setting of
	 * the SPE discriminator, DAC channel 9, and the scaler deadtime.
	 * @return integer-valued counts of SPE rate meter in 1-sec exposure interval
	 */
	public int getSPEScaler() { return record.getInt(76); }
	
	/**
	 * Get the MPE rate meter counts.  This counter is affected by the setting of
	 * the MPE discriminator, DAC channel 8, and the scaler deadtime.
	 * @return integer-valued counts of MPE rate meter in 1-sec exposure interval
	 */
	public int getMPEScaler() { return record.getInt(80); }
}

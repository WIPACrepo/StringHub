package icecube.daq.domapp;

public class ReadoutFormat 
{
	public boolean compressionOn;
	public EngineeringRecordFormat engineeringFormat;
	
	public ReadoutFormat()
	{
		compressionOn = false;
		engineeringFormat = new EngineeringRecordFormat();
	}
	
	public ReadoutFormat(ReadoutFormat copyFrom)
	{
		this.compressionOn = copyFrom.compressionOn;
		this.engineeringFormat = new EngineeringRecordFormat(copyFrom.engineeringFormat);
	}
}

package icecube.daq.util;

/**
 * This class' sole purpose is to hold information about DOMs
 * that are <i>permanently</i> installed in the ice (or in IceTop). 
 * @author krokodil
 */

public class DeployedDOM 
{
	String mainboardId;
	String domId;
	String name;
	int string;
	int location;
	double x;
	double y;
	double z;
	
	/** Constructor only for package peers */
	DeployedDOM() { }
	
	/**
	 * Copy construtor.
	 */
	DeployedDOM(DeployedDOM dom)
	{
		mainboardId = dom.mainboardId;
		domId 		= dom.domId;
		name  		= dom.name;
		string 		= dom.string;
		location 	= dom.location;
		x			= dom.x;
		y			= dom.y;
		z 			= dom.z;
	}

	public String getMainboardId() { return mainboardId; }
	
	public String getDomId() { return domId; }
	
	public String getName() { return name; }
	
	public int getStringMajor() { return string; }
	
	public int getStringMinor() { return location; }
	
	public double getX() { return x; }
	public double getY() { return y; }
	public double getZ() { return z; }
	
}

package icecube.daq.stringhub;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * This TestShell will run the StringHub component outside
 * of the distributed component framework.  But it's really
 * very thin at the moment.
 * @author krokodil
 *
 */
public class TestShell
{

	public static void main(String[] args) throws Exception
	{
		BasicConfigurator.configure();
		Logger.getRootLogger().setLevel(Level.DEBUG);
		int hubId = 0;
		try
		{
			hubId = Integer.getInteger("icecube.daq.stringhub.componentId");
		}
		catch (Exception ex)
		{
			System.err.println("Component Id not set - specify with -Dicecube.daq.stringhub.componentId=X");
			System.exit(1);
		}

		StringHubComponent comp = new StringHubComponent(hubId);
		int iarg = 0;

		comp.setGlobalConfigurationDir(args[iarg++]);
        comp.configuring(args[iarg++]);
        comp.starting(0);
        Thread.sleep(5000);
        comp.stopping();

	}


}

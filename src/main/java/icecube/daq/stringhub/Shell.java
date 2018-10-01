package icecube.daq.stringhub;

import icecube.daq.juggler.component.DAQCompServer;

/**
 * The Shell is a running frame for the StringHub
 * @author krokodil
 *
 */
public class Shell
{

	public static void main(String[] args) throws Exception
	{

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

		DAQCompServer srvr;
		try {
			srvr = new DAQCompServer( new StringHubComponent(hubId), args );
		} catch (IllegalArgumentException ex) {
			System.err.println(ex.getMessage());
			System.exit(1);
			return; // without this, compiler whines about uninitialized 'srvr'
		}
		srvr.startServing();
	}

}

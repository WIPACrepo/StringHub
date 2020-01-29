package icecube.daq.stringhub;

import icecube.daq.juggler.component.DAQCompServer;

import java.io.PrintWriter;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

/**
 * The Shell is a running frame for the StringHub
 * @author krokodil
 *
 */
public class Shell
{
	public static void main(String[] args) throws Exception
	{
		ConsoleAppender appender = new ConsoleAppender();
		appender.setWriter(new PrintWriter(System.out));
		appender.setLayout(new PatternLayout("%p[%t] %L - %m%n"));
		appender.setName("console");
		Logger.getRootLogger().addAppender(appender);
		Logger.getRootLogger().setLevel(Level.INFO);

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

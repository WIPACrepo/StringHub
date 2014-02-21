/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */
package icecube.daq.stringhub;

import icecube.daq.common.DAQCmdInterface;
import icecube.daq.io.DAQComponentInputProcessor;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.OutputChannel;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.SourceIdRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

class InternalHub
	extends StringHubComponent
{
	//Not used
	//private int runNumber;

	InternalHub(int hubId)
	{
		super(hubId, (hubId >= 1000 && hubId < 2000), false, true, false,
			  false, false, false, false);
	}

	public void addCache(IByteBufferCache cache)
	{
	}

	public void addCache(String name, IByteBufferCache cache)
	{
	}

	public void addMBean(String name, Object mbean)
	{
	}

	public void addMonitoredEngine(String type,
								   DAQComponentInputProcessor engine)
	{
	}

	public void addMonitoredEngine(String type,
								   DAQComponentOutputProcess engine)
	{
	}

	public void addOptionalEngine(String type,
								  DAQComponentOutputProcess engine)
	{
	}

	public void connect(String host, int port)
		throws IOException
	{
		connectEngine(getTrackEngineWriter(), getCache(),
					  DAQCmdInterface.DAQ_TRACK_ENGINE, 0, host, port);
	}

	public OutputChannel connectEngine(DAQComponentOutputProcess engine,
									   IByteBufferCache bufMgr,
									   String name, int num,
									   String host, int port)
		throws IOException
	{
		InetSocketAddress addr =
			new InetSocketAddress(host, port);

		SocketChannel chan;
		try {
			chan = SocketChannel.open(addr);
		} catch (UnresolvedAddressException uae) {
			throw new IllegalArgumentException("Unresolved address " +
											   host + ":" + port, uae);
		}

		chan.configureBlocking(false);

		final int srcId =
			SourceIdRegistry.getSourceIDFromNameAndId(name, num % 1000);
		return engine.connect(bufMgr, chan, srcId);
	}

	public Alerter getAlerter()
	{
		return null;
	}

	public void startOutput()
	{
		getTrackEngineWriter().startProcessing();
	}
}

public class TEHub
{
	private static final String ID_PROPERTY =
		"icecube.daq.stringhub.componentId";

	private static final int TE_PORT = 9000;

	private String configDir;
	private String runConfig;
	private String teHost;
	private int tePort = TE_PORT;
	private int runNumber;

	TEHub(String[] args)
		throws DAQCompException, IOException
	{
		processArgs(args);

		createAndRun();
	}

	private void createAndRun()
		throws DAQCompException, IOException
	{

		int hubId = 0;
		try {
			hubId = Integer.getInteger(ID_PROPERTY);
		} catch (Exception ex) {
			System.err.println("Component Id not set - specify with -D" +
							   ID_PROPERTY + "=X");
			System.exit(1);
		}

		InternalHub hub = new InternalHub(hubId);

		hub.setGlobalConfigurationDir(configDir);
		hub.setRunNumber(runNumber);

		hub.connect(teHost, tePort);

		hub.configure(runConfig, false);

		hub.starting();

		hub.startOutput();
	}

	/**
	 * Process command-line arguments.
	 */
	private void processArgs(String[] args)
	{
		boolean usage = false;
		for (int i = 0; i < args.length; i++) {
			if (args[i].length() > 1 && args[i].charAt(0) == '-') {
				switch(args[i].charAt(1)) {
				case 'c':
					runConfig = args[++i];
					break;
				case 'g':
					configDir = args[++i];
					break;
				case 'h':
					teHost = args[++i];
					break;
				case 'n':
					try {
						runNumber = Integer.parseInt(args[++i]);
					} catch (NumberFormatException nfe) {
						System.err.println("Bad run number '" + args[i-1] +
										   "'");
						usage = true;
					}
					break;
				}
			} else if (args[i].length() > 0) {
				System.err.println("Unknown argument '" + args[i] + "'");
				usage = true;
			}
		}

		if (usage) {
			String usageMsg = "java " + getClass().getName() +
				" [-c runConfigName]" +
				" [-g globalConfigPath]" +
				" [-h trackEngineHost]" +
				"";
			throw new IllegalArgumentException(usageMsg);
		}
	}

	public static final void main(String[] args)
		throws DAQCompException, IOException
	{
		org.apache.log4j.BasicConfigurator.configure();
		Logger.getRootLogger().setLevel(Level.ERROR);

		new TEHub(args);
	}
}

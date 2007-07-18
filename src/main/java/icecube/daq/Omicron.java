package icecube.daq;

import icecube.daq.bindery.BufferConsumerChannel;
import icecube.daq.bindery.StreamBinder;
import icecube.daq.configuration.XMLConfig;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.RunLevel;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class Omicron {

	private static Driver driver = Driver.getInstance();
	private static ArrayList<DataCollector> collectors;
	//private static ByteBuffer drain;
	private static final Logger logger = Logger.getLogger(Omicron.class);
	
	public static void main(String[] args) throws Exception 
	{
		int index = 0;
		float runLength = 30.0f;
		String pathToProps = ".omicron.properties";
		
		while (index < args.length)
		{
			String arg = args[index];
			if (arg.charAt(0) != '-') break;
			switch (arg.charAt(1))
			{
			case 't': // run time setting
				runLength = Float.parseFloat(arg.substring(2));
				break;
			case 'P': // properties file specifier
				pathToProps = arg.substring(2);
				break;
			}
			index++;
		}

		if (args.length - index < 2)
		{
			System.err.println("usage : java ic3.daq.Omicron [ opts ] <output-file> <xml-config>");
			System.exit(1);
		}
		
		Properties props = new Properties();
		props.load(new FileInputStream(pathToProps));
		PropertyConfigurator.configure(props);

		long  runLengthMsec = (long) (1000.0 * runLength);

		String outputBaseName = args[index++];
		FileOutputStream fOutHits = new FileOutputStream(outputBaseName + ".hits");
		XMLConfig xmlConfig = new XMLConfig();
		xmlConfig.parseXMLConfig(new FileInputStream(args[index++]));
		
		logger.info("Begin logging at " + new java.util.Date());
		collectors = new ArrayList<DataCollector>();
		
		// Must first count intersection of configured and discovered DOMs
		int nDOM = 0;
		List<DOMChannelInfo> activeDOMs = driver.discoverActiveDOMs();
		for (DOMChannelInfo chInfo : activeDOMs)
			if (xmlConfig.getDOMConfig(chInfo.mbid) != null) nDOM++;

        BufferConsumerChannel chan = new BufferConsumerChannel(fOutHits.getChannel());
		StreamBinder bind = new StreamBinder(nDOM, chan);	

		for (DOMChannelInfo chInfo : activeDOMs) 
		{
			DOMConfiguration config = xmlConfig.getDOMConfig(chInfo.mbid);
			if (config == null) continue;
			String cwd = chInfo.card + "" + chInfo.pair + chInfo.dom;
			Pipe pipe = Pipe.open();
			FileOutputStream fOutMoni = new FileOutputStream(outputBaseName + "-" + chInfo.mbid + ".moni");
			DataCollector dc = new DataCollector(
					chInfo.card, chInfo.pair, chInfo.dom, config, 
					pipe.sink(), fOutMoni.getChannel(), null, null
					);
			bind.register(pipe.source(), cwd);
			collectors.add(dc);
			logger.debug("Starting new DataCollector thread on (" + chInfo.card + "" + chInfo.pair + "" + chInfo.dom + ").");
			dc.start();
			logger.debug("DataCollector thread on (" + chInfo.card + "" + chInfo.pair + "" + chInfo.dom + ") started.");
		}
		
		bind.start();
		
		// All collectors are now started at latest by t0
		long t0 = System.currentTimeMillis();
		
		// wait about 1.5 sec for DOMs to open up
		logger.info("Sleeping for DOM devfile open");
		Thread.sleep(10000);
		
		// List of objects that need removal
		ArrayList<DataCollector> reaper = new ArrayList<DataCollector>();
		
		// Do not need to wait on init - you can signal immediately that the
		// DataCollector should be configured

		logger.info("Sending CONFIGURE signal to DataCollectors");
		
		for (DataCollector dc : collectors) 
		{
			if (!dc.isAlive())
			{
				logger.warn("Collector " + dc.getName() + " died before config: schedule for removal.");
				reaper.add(dc);
			}
			else
			{
				dc.signalConfigure();
			}
		}
		
		collectors.removeAll(reaper);
		reaper.clear();
		
		logger.info("Waiting on DOMs to configure...");
		
		// Wait until configured
		for (DataCollector dc : collectors)
		{
			if (!dc.isAlive())
			{
				logger.warn("Collector " + dc.getName() + " died during config: schedule for removal.");
				reaper.add(dc);
			}
			else
			{
				while (!dc.getRunLevel().equals(RunLevel.CONFIGURED) && System.currentTimeMillis() - t0 < 15000L)
				{
					logger.debug("Waiting of DC " + dc.getName() + " to configure.");
					Thread.sleep(500);
				}
				if (!dc.getRunLevel().equals(RunLevel.CONFIGURED))
				{
					logger.warn("Collector " + dc.getName() + " stuck configuring: coup de grace.");
					dc.interrupt();
					reaper.add(dc);
				}
			}
		}
		
		collectors.removeAll(reaper);
		reaper.clear();
		
		logger.info("Starting run...");
		
		// Quickly fire off a run start now that all are ready
		for (DataCollector dc : collectors) 
			if (dc.isAlive()) dc.signalStartRun();
		
		t0 = System.currentTimeMillis();
		
		while (true) 
		{
			long time = System.currentTimeMillis(); 
			if (time - t0 > runLengthMsec) 
			{
				for (DataCollector dc : collectors) if (dc.isAlive()) dc.signalStopRun();
				break;
			}
			Thread.sleep(1000);
		}
		
		for (DataCollector dc : collectors) {
			while (dc.isAlive() && !dc.getRunLevel().equals(RunLevel.CONFIGURED)) Thread.sleep(100);
			dc.signalShutdown();
		}

		bind.join();
	}
}

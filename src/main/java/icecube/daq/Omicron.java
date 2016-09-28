package icecube.daq;

import icecube.daq.bindery.BufferConsumerAsync;
import icecube.daq.bindery.BufferConsumerBuffered;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.configuration.XMLConfig;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.DataCollectorFactory;
import icecube.daq.domapp.RunLevel;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.performance.diagnostic.DataCollectorAggregateContent;
import icecube.daq.performance.diagnostic.DiagnosticTrace;
import icecube.daq.performance.diagnostic.MeterContent;
import icecube.daq.performance.diagnostic.Metered;
import icecube.daq.time.gps.GPSService;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class Omicron {

    private static Driver driver = Driver.getInstance();
    private static ArrayList<DataCollector> collectors;
    //private static ByteBuffer drain;
    private static final Logger logger = Logger.getLogger(Omicron.class);
    private static final boolean DISABLE_INTERVAL = Boolean.getBoolean("icecube.daq.domapp.datacollector.disable_intervals");

    // ext-3 on scube has a block size of 4K.  Buffer 10 blocks
    private static final int BUFFER_SIZE = 40960;

	public static void main(String[] args) throws Exception
	{
        // needs to be done soon as possible.
        setGPSConfiguration();

		int index = 0;
		float runLength = 30.0f;
		String pathToProps = ".omicron.properties";

		while (index < args.length)
		{
			String opt = args[index];
			String arg = null;
			if (opt.charAt(0) != '-') break;
			index += 1;
			switch (opt.charAt(1))
			{

			case 't': // run time setting
			    if (opt.length() == 2)
			        arg = args[index++];
			    else
			        arg = opt.substring(2);
				runLength = Float.parseFloat(arg);
				break;
			case 'P': // properties file specifier
			    if (opt.length() == 2)
			        arg = args[index++];
			    else
			        arg = opt.substring(2);
				pathToProps = arg;
				break;
			}
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
		XMLConfig xmlConfig = new XMLConfig();
		xmlConfig.parseXMLConfig(new FileInputStream(args[index++]));

		if (logger.isInfoEnabled()) {
			logger.info("Begin logging at " + new java.util.Date());
		}
		collectors = new ArrayList<DataCollector>();

		// Must first count intersection of configured and discovered DOMs
		int nDOM = 0;
		List<DOMChannelInfo> activeDOMs = driver.discoverActiveDOMs();
		for (DOMChannelInfo chInfo : activeDOMs)
			if (xmlConfig.getDOMConfig(chInfo.mbid) != null) nDOM++;

        // set up trace for the processing stack
        DiagnosticTraceConfig trace = new DiagnosticTraceConfig();
        Metered.Buffered sortQueueMeter = trace.getSortQueueMeter();
        Metered.UTCBuffered sortMeter = trace.getSortMeter();
        Metered.Buffered hitConsumerMeter =  trace.getAsyncHitConsumerMeter();

        //FileOutputStream fOutHits = new FileOutputStream(outputBaseName + ".hits");

		BufferedOutputStream fOutHits = new BufferedOutputStream(new FileOutputStream(outputBaseName+".hits"), BUFFER_SIZE);
		BufferedOutputStream fOutMoni = new BufferedOutputStream(new FileOutputStream(outputBaseName+".moni"), BUFFER_SIZE);
		BufferedOutputStream fOutTcal = new BufferedOutputStream(new FileOutputStream(outputBaseName+".tcal"), BUFFER_SIZE);
		BufferedOutputStream fOutScal = new BufferedOutputStream(new FileOutputStream(outputBaseName+".scal"), BUFFER_SIZE);

		BufferConsumerBuffered hitsChan = new BufferConsumerBuffered(fOutHits);
		BufferConsumerBuffered moniChan = new BufferConsumerBuffered(fOutMoni);
		BufferConsumerBuffered tcalChan = new BufferConsumerBuffered(fOutTcal);
		BufferConsumerBuffered scalChan = new BufferConsumerBuffered(fOutScal);

        // consume hits on a dedicated thread
        BufferConsumerAsync asyncHitConsumer = new BufferConsumerAsync(hitsChan, 500000, "hit-consumer", hitConsumerMeter);
        MultiChannelMergeSort hitsSort = new MultiChannelMergeSort(nDOM, asyncHitConsumer, "hits", sortQueueMeter, sortMeter);
		MultiChannelMergeSort moniSort = new MultiChannelMergeSort(nDOM, moniChan, "moni");
		MultiChannelMergeSort tcalSort = new MultiChannelMergeSort(nDOM, tcalChan, "tcal");
		MultiChannelMergeSort scalSort = new MultiChannelMergeSort(nDOM, scalChan, "supernova");

		for (DOMChannelInfo chInfo : activeDOMs)
		{
			DOMConfiguration config = xmlConfig.getDOMConfig(chInfo.mbid);
			if (config == null) continue;
			hitsSort.register(chInfo.getMainboardIdAsLong());
			moniSort.register(chInfo.getMainboardIdAsLong());
			tcalSort.register(chInfo.getMainboardIdAsLong());
			scalSort.register(chInfo.getMainboardIdAsLong());

			// Associate a GPS service to this card, if not already done
			GPSService.getInstance().startService(chInfo.card);

			DataCollector dc =
                    DataCollectorFactory.buildDataCollector(
					chInfo.card, chInfo.pair, chInfo.dom, chInfo.mbid, config,
                    hitsSort, moniSort, scalSort, tcalSort,
					!DISABLE_INTERVAL);
			collectors.add(dc);
			if (logger.isDebugEnabled()) logger.debug("Starting new DataCollector thread on (" + chInfo.card + "" + chInfo.pair + "" + chInfo.dom + ").");
			if (logger.isDebugEnabled()) logger.debug("DataCollector thread on (" + chInfo.card + "" + chInfo.pair + "" + chInfo.dom + ") started.");
		}

        trace.startTrace(collectors);

        hitsSort.start();
		moniSort.start();
		scalSort.start();
		tcalSort.start();

		// All collectors are now started at latest by t0
		long t0 = System.currentTimeMillis();

		// List of objects that need removal
		HashSet<DataCollector> reaper = new HashSet<DataCollector>();

		if (logger.isInfoEnabled()) {
			logger.info("Waiting for collectors to initialize");
		}
		for (DataCollector dc : collectors)
		{
		    // Note that if you turn SN data off on all doms the extra
		    // messaging pushed the us over the timeout here
		    // doubling the timeout worked.
            while (dc.isAlive() &&
                    !dc.getRunLevel().equals(RunLevel.IDLE) &&
                    System.currentTimeMillis() - t0 < 30000L)
                Thread.sleep(100);
		    if (!dc.isAlive())
		    {
		        logger.warn("Collector " + dc.getName() + " died in init.");
		        reaper.add(dc);
		    }
		}

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
                while (!dc.getRunLevel().equals(RunLevel.CONFIGURED) && System.currentTimeMillis() - t0 < 30000L)
				{
					if (logger.isDebugEnabled()) logger.debug("Waiting of DC " + dc.getName() + " to configure.");
					Thread.sleep(500);
				}
				if (!dc.getRunLevel().equals(RunLevel.CONFIGURED))
				{
					logger.warn("Collector " + dc.getName() + " stuck configuring: coup de grace.");
                    dc.signalShutdown();
					reaper.add(dc);
				}
			}
		}

		collectors.removeAll(reaper);
		reaper.clear();

		logger.info("Starting run...");



        if(collectors.size() > 0)
        {

            // Quickly fire off a run start now that all are ready
            for (DataCollector dc : collectors)
                if (dc.isAlive()) dc.signalStartRun();

            t0 = System.currentTimeMillis() + runLengthMsec;

            while (true)
            {
                long time = System.currentTimeMillis();
                if (time > t0)
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
        }
        else
        {
            logger.warn("No DataCollectors left to start, aborting run");
        }

        hitsSort.join();
        asyncHitConsumer.join();

        moniSort.join();
        scalSort.join();
        tcalSort.join();

        // kill GPS services
        GPSService.getInstance().shutdownAll();

        // not sure if this is needed, but close the output file
        fOutHits.close();
        fOutMoni.close();
        fOutTcal.close();
        fOutScal.close();

        // kill trace
        trace.stopTrace();
    }



    /**
     * Replace the default GPS configuration (which requires GPS) with a
     * more forgiving setting that falls back to a works-all-the-time
     * configuration, unless the user configured the mode explicitly.
     *
     * Note: This needs to be called before the GPSService class
     *       is instantiated.
     */
    private static void setGPSConfiguration()
    {
        String userSetting =
                System.getProperty("icecube.daq.time.gps.gps-mode");

        if(userSetting == null)
        {
            System.setProperty("icecube.daq.time.gps.gps-mode", "discover");
        }
    }

    /**
     * Encapsulates the optional injection of a performance trace into the
     * hit processing stack.
     */
    private static class DiagnosticTraceConfig
    {

        private static boolean enabled =
                Boolean.getBoolean("omicron.trace.enabled");

        private static int period =
                Integer.getInteger("omicron.trace.period", 1000);

        final Metered.Buffered sortQueueMeter;
        final Metered.UTCBuffered sortMeter;
        final Metered.Buffered asyncHitConsumerMeter;

        DiagnosticTrace trace;

        DiagnosticTraceConfig()
        {
            if(enabled)
            {
                sortQueueMeter = Metered.Factory.bufferMeter(
                        Metered.Factory.ConcurrencyModel.MPMC);
                sortMeter = Metered.Factory.utcBufferMeter();
                asyncHitConsumerMeter = Metered.Factory.bufferMeter();
            }
            else
            {
                sortQueueMeter = new Metered.DisabledMeter();
                sortMeter = new Metered.DisabledMeter();
                asyncHitConsumerMeter = new Metered.DisabledMeter();
            }
        }

        Metered.Buffered getSortQueueMeter()
        {
            return sortQueueMeter;
        }

        Metered.UTCBuffered getSortMeter()
        {
            return sortMeter;
        }

        Metered.Buffered getAsyncHitConsumerMeter()
        {
            return asyncHitConsumerMeter;
        }

        private void startTrace(List<DataCollector> collectors)
        {
            if(enabled)
            {
                trace = new DiagnosticTrace(period, 30);
                trace.addTimeContent();
                trace.addAgeContent();
                trace.addHeapContent();
                trace.addGCContent();
                trace.addContent(new DataCollectorAggregateContent(collectors));
                trace.addMeter("sortq", sortQueueMeter, MeterContent.Style.HELD_DATA);
                trace.addMeter("sorter", sortMeter, MeterContent.Style.HELD_DATA,
                        MeterContent.Style.UTC_DELAY,
                        MeterContent.Style.DATA_RATE_OUT);
                trace.addMeter("hitOut", asyncHitConsumerMeter,
                        MeterContent.Style.HELD_DATA,
                        MeterContent.Style.DATA_RATE_OUT);

                trace.start();
            }
        }

        private void stopTrace()
        {
            if(enabled)
            {
                if(trace != null)
                {
                    trace.stop();
                }
            }
        }
    }
}

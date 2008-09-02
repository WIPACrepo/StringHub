/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */
package icecube.daq.stringhub;

import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.bindery.SecondaryStreamConsumer;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.configuration.XMLConfig;
import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.RunLevel;
import icecube.daq.domapp.SimDataCollector;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.OutputChannel;
import icecube.daq.io.PayloadReader;
import icecube.daq.io.SimpleOutputEngine;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.mbean.MemoryStatistics;
import icecube.daq.juggler.mbean.SystemStatistics;
import icecube.daq.monitoring.DataCollectorMonitor;
import icecube.daq.monitoring.MonitoringData;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.VitreousBufferCache;
import icecube.daq.sender.RequestReader;
import icecube.daq.sender.Sender;
import icecube.daq.trigger.component.GlobalConfiguration;
import icecube.daq.trigger.config.TriggerBuilder;
import icecube.daq.trigger.control.IStringTriggerHandler;
import icecube.daq.trigger.control.ITriggerControl;
import icecube.daq.trigger.control.StringTriggerHandler;
import icecube.daq.util.DOMRegistry;
import icecube.daq.util.DeployedDOM;
import icecube.daq.util.FlasherboardConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

public class StringHubComponent extends DAQComponent
{

	private static final Logger logger = Logger.getLogger(StringHubComponent.class);

    private int hubId;
	private boolean isSim = false;
	private Driver driver = Driver.getInstance();
	private IByteBufferCache cache;
	private Sender sender;
	private MasterPayloadFactory payloadFactory;
	private DOMRegistry domRegistry;
	private IByteBufferCache moniBufMgr, tcalBufMgr, snBufMgr;
	private PayloadReader reqIn;
	private SimpleOutputEngine moniOut;
	private SimpleOutputEngine tcalOut;
	private SimpleOutputEngine supernovaOut;
	private SimpleOutputEngine hitOut;
	private SimpleOutputEngine dataOut;
	private DOMConnector conn = null;
	private List<DOMChannelInfo> activeDOMs;
	private MultiChannelMergeSort hitsSort;
    private MultiChannelMergeSort moniSort;
    private MultiChannelMergeSort tcalSort;
    private MultiChannelMergeSort scalSort;
	private String configurationPath;
	private String configured = "NO";
	private int nch;
	private DataCollectorMonitor collectorMonitor;

	private boolean enableTriggering = false;
	private ISourceID sourceId;
	private IStringTriggerHandler triggerHandler;
	private static final String COMPONENT_NAME = DAQCmdInterface.DAQ_STRING_HUB;

	public StringHubComponent(int hubId)
	{
		this(hubId, (hubId >= 1000 && hubId < 2000));
	}

	public StringHubComponent(int hubId, boolean isSim)
	{
		super(COMPONENT_NAME, hubId);

        this.hubId = hubId;
		this.isSim = isSim;

		final String bufName = "PyrateBufferManager";

		cache  = new VitreousBufferCache();
		addCache(cache);
		addMBean(bufName, cache);

		addMBean("jvm", new MemoryStatistics());
		addMBean("system", new SystemStatistics());

		payloadFactory = new MasterPayloadFactory(cache);
		sender         = new Sender(hubId, payloadFactory);
		nch            = 0;

		if (logger.isInfoEnabled()) {
			logger.info("starting up StringHub component " + hubId);
		}

        /*
         * Component derives behavioral characteristics from
         * its 'minor ID' - they are ...
         * (1) component xx81 - xx99 : icetop
         * (2) component xx01 - xx80 : in-ice
         * (3) component xx00        : amandaHub
         */
        int minorHubId = hubId % 100;

        hitOut = null;

        if (minorHubId > 0)
        {
            hitOut = new SimpleOutputEngine(COMPONENT_NAME, hubId, "hitOut");
            if (minorHubId > 80)
                addMonitoredEngine(DAQConnector.TYPE_ICETOP_HIT, hitOut);
            else
                addMonitoredEngine(DAQConnector.TYPE_STRING_HIT, hitOut);
            sender.setHitOutput(hitOut);
        }


        try
        {
            reqIn = new RequestReader(COMPONENT_NAME, sender, payloadFactory);
        }
        catch (IOException ioe)
        {
            throw new Error("Couldn't create RequestReader", ioe);
        }
        addMonitoredEngine(DAQConnector.TYPE_READOUT_REQUEST, reqIn);

		dataOut =
            new SimpleOutputEngine(COMPONENT_NAME, hubId, "dataOut");
        addMonitoredEngine(DAQConnector.TYPE_READOUT_DATA, dataOut);

        sender.setDataOutput(dataOut);

        MonitoringData monData = new MonitoringData();
        monData.setSenderMonitor(sender);
        addMBean("sender", monData);

		collectorMonitor = new DataCollectorMonitor();
		addMBean("datacollectormonitor", collectorMonitor);

        // Following are the payload output engines for the secondary streams
		moniBufMgr  = new VitreousBufferCache();
		addCache(DAQConnector.TYPE_MONI_DATA, moniBufMgr);
        moniOut = new SimpleOutputEngine(COMPONENT_NAME, hubId, "moniOut");
        addMonitoredEngine(DAQConnector.TYPE_MONI_DATA, moniOut);

		tcalBufMgr  = new VitreousBufferCache();
		addCache(DAQConnector.TYPE_TCAL_DATA, tcalBufMgr);
        tcalOut = new SimpleOutputEngine(COMPONENT_NAME, hubId, "tcalOut");
        addMonitoredEngine(DAQConnector.TYPE_TCAL_DATA, tcalOut);

		snBufMgr  = new VitreousBufferCache();
		addCache(DAQConnector.TYPE_SN_DATA, snBufMgr);
        supernovaOut = new SimpleOutputEngine(COMPONENT_NAME, hubId, "supernovaOut");
        addMonitoredEngine(DAQConnector.TYPE_SN_DATA, supernovaOut);
    }

	@Override
	public void setGlobalConfigurationDir(String dirName)
	{
		super.setGlobalConfigurationDir(dirName);
		configurationPath = dirName;
		if (logger.isInfoEnabled()) {
			logger.info("Setting the ueber configuration directory to " + configurationPath);
		}
        // get a reference to the DOM registry - useful later
        try {
			domRegistry = DOMRegistry.loadRegistry(configurationPath);
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		} catch (SAXException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		}
	}

	/**
	 * This method will force the string hub to query the driver for a list of DOMs.
	 * For a DOM to be detected its cardX/pairY/domZ/id procfile must report a valid
	 * non-zero DOM mainboard ID.
	 * @throws IOException
	 */
	private void discover() throws IOException, DocumentException
	{
		if (isSim)
		{
			ArrayList<DeployedDOM> attachedDOMs = domRegistry.getDomsOnString(getNumber());
			activeDOMs = new ArrayList<DOMChannelInfo>(attachedDOMs.size());
			for (DeployedDOM dom : attachedDOMs)
			{
				int card = (dom.getStringMinor()-1) / 8;
				int pair = ((dom.getStringMinor()-1) % 8) / 2;
				char aorb = 'A';
				if (dom.getStringMinor() % 2 == 1) aorb = 'B';
				activeDOMs.add(new DOMChannelInfo(dom.getMainboardId(), card, pair, aorb));
			}
		}
		else
		{
		    // put the driver into blocking mode
		    driver.setBlocking(true);
			activeDOMs = driver.discoverActiveDOMs();
			if (logger.isInfoEnabled()) {
				logger.info("Found " + activeDOMs.size() + " active DOMs.");
			}
		}
	}

	private void enableTriggering()
	{
	    if (hitOut == null) return;

        sourceId = SourceIdRegistry.getISourceIDFromNameAndId(COMPONENT_NAME, hubId);
        triggerHandler = new StringTriggerHandler(sourceId);
        triggerHandler.setMasterPayloadFactory(payloadFactory);
        triggerHandler.setPayloadOutput(hitOut);

        // feed sender output through string trigger
        sender.setHitOutput(triggerHandler);
        logger.info("triggering enabled");
	}

	/**
	 * StringHub responds to a configure request from the controller
	 */
	@SuppressWarnings("unchecked")
    public void configuring(String configName) throws DAQCompException
	{

		String realism;

		if (isSim)
			realism = "SIMULATION";
		else
			realism = "REAL DOMS";

		configured = "YES";

		try
		{
			// Lookup the connected DOMs
			discover();

			if (activeDOMs.size() == 0)
			    throw new DAQCompException("No Active DOMs on hub.");

			// Parse out tags from 'master configuration' file
			File domConfigsDirectory = new File(configurationPath, "domconfigs");
			File masterConfigFile = new File(configurationPath, configName + ".xml");
			FileInputStream fis = new FileInputStream(masterConfigFile);

			SAXReader r = new SAXReader();
			Document doc = r.read(fis);

			XMLConfig xmlConfig = new XMLConfig();
			List<Node> configNodeList = doc.selectNodes("runConfig/domConfigList");
			/*
			 * Lookup <stringHub hubId='x'> node - if any - and process
			 * configuration directives.
			 */
			Node hubNode = doc.selectSingleNode("runConfig/stringHub[@hubId='" + hubId + "']");
			if (hubNode != null)
			{
			    if (hubNode.valueOf("trigger/enabled").equalsIgnoreCase("true")) enableTriggering();
			    if (hubNode.valueOf("sender/forwardIsolatedHitsToTrigger").equalsIgnoreCase("true"))
			        sender.forwardIsolatedHitsToTrigger();
			}
			if (logger.isInfoEnabled()) {
				logger.info("Number of domConfigNodes found: " + configNodeList.size());
			}
			for (Node configNode : configNodeList) {
				String tag = configNode.getText();
				if (!tag.endsWith(".xml"))
					tag = tag + ".xml";
				File configFile = new File(domConfigsDirectory, tag);
				if (logger.isInfoEnabled()) {
					logger.info("Configuring " + realism
							+ " - loading config from "
							+ configFile.getAbsolutePath());
				}
				xmlConfig.parseXMLConfig(new FileInputStream(configFile));
			}

			fis.close();

			// Find intersection of discovered / configured channels
			nch = 0;
			for (DOMChannelInfo chanInfo : activeDOMs)
				if (xmlConfig.getDOMConfig(chanInfo.mbid) != null) nch++;

			if (nch == 0)
			    throw new DAQCompException("No Active DOMs on Hub selected in configuration.");

			if (logger.isInfoEnabled()) {
				logger.info("Configuration successfully loaded - Intersection(DISC, CONFIG).size() = " + nch);
			}

			// Must make sure to release file resources associated with the previous
			// runs since we are throwing away the collectors and starting from scratch
			if (conn != null) conn.destroy();

			conn = new DOMConnector(nch);

			SecondaryStreamConsumer monitorConsumer   = new SecondaryStreamConsumer(hubId, moniBufMgr, moniOut.getChannel());
	        SecondaryStreamConsumer supernovaConsumer = new SecondaryStreamConsumer(hubId, snBufMgr, supernovaOut.getChannel());
	        SecondaryStreamConsumer tcalConsumer      = new SecondaryStreamConsumer(hubId, tcalBufMgr, tcalOut.getChannel());

			// Start the merger-sorter objects
			hitsSort = new MultiChannelMergeSort(nch, sender);
			moniSort = new MultiChannelMergeSort(nch, monitorConsumer);
			scalSort = new MultiChannelMergeSort(nch, supernovaConsumer);
			tcalSort = new MultiChannelMergeSort(nch, tcalConsumer);

			for (DOMChannelInfo chanInfo : activeDOMs)
			{
				DOMConfiguration config = xmlConfig.getDOMConfig(chanInfo.mbid);
				if (config == null) continue;

				String cwd = chanInfo.card + "" + chanInfo.pair + chanInfo.dom;

				AbstractDataCollector dc;

				if (isSim)
				{
					boolean isAmanda = (getNumber() % 100) == 0;

					dc = new SimDataCollector(chanInfo, config,
					        hitsSort,
					        moniSort,
					        scalSort,
					        tcalSort,
					        isAmanda);
				}
				else
				{
					dc = new DataCollector(
							chanInfo.card, chanInfo.pair, chanInfo.dom, config,
							hitsSort,
							moniSort,
							scalSort,
							tcalSort,
							null,
							null,
							null);
				}

				hitsSort.register(chanInfo.mbid_numerique);
				moniSort.register(chanInfo.mbid_numerique);
				scalSort.register(chanInfo.mbid_numerique);
				tcalSort.register(chanInfo.mbid_numerique);
				conn.add(dc);
				if (logger.isDebugEnabled()) logger.debug("Starting new DataCollector thread on (" + cwd + ").");
			}

			logger.info("Starting up HKN1 sorting trees...");

			// Still need to get the data collectors to pick up and do something with the config
			conn.configure();

			collectorMonitor.setConnector(conn);
		}

		catch (FileNotFoundException fnx)
		{
			logger.error("Could not find the configuration file.");
			throw new DAQCompException(fnx.getMessage());
		}
		catch (IOException iox)
		{
			iox.printStackTrace();
			logger.error("Caught IOException - " + iox.getMessage());
			throw new DAQCompException(iox.getMessage());
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new DAQCompException(e.getMessage());
		}


        // If triggers are enabled, configure them
        if (enableTriggering) {
            configureTrigger(configName);
        }

    }

	/**
	 * Controller wants StringHub to start sending data.  Tell DOMs to start up.
	 */
	public void starting() throws DAQCompException
	{
	    logger.info("StringHub is starting the run.");

	    hitsSort.start();
	    moniSort.start();
	    scalSort.start();
	    tcalSort.start();

		try
		{
			conn.startProcessing();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new DAQCompException("Couldn't start DOMs", e);
		}
	}

	public long startSubrun(List<FlasherboardConfiguration> flasherConfigs) throws DAQCompException
	{
	    /*
	     * Useful to keep operators from accidentally powering up two
	     * flasherboards simultaneously.
	     */
	    boolean[] wirePairSemaphore = new boolean[32];
	    long validXTime = 0L;

	    logger.info("Beginning subrun");

        for (AbstractDataCollector adc : conn.getCollectors())
        {
            String mbid = adc.getMainboardId();
            FlasherboardConfiguration flasherConfig = null;

            // Hunt for this DOM channel in the flasher config list
            for (FlasherboardConfiguration fbc : flasherConfigs)
            {
	            if (fbc.getMainboardID().equals(mbid))
	            {
	                flasherConfig = fbc;
	                break;
	            }
            }

            if (flasherConfig != null)
            {
                int pairIndex = 4 * adc.getCard() + adc.getPair();
                if (wirePairSemaphore[pairIndex])
                    throw new DAQCompException("Cannot activate > 1 flasher run per DOR wire pair.");
                wirePairSemaphore[pairIndex] = true;
            }

            boolean stateChange = flasherConfig != null || adc.getFlasherConfig() != null;
            if (stateChange)
            {
                adc.setFlasherConfig(flasherConfig);
                adc.signalStartSubRun();
            }
	    }

        for (AbstractDataCollector adc : conn.getCollectors())
        {
            if (adc.getRunLevel() == RunLevel.ZOMBIE) continue;
            try
            {
                while (adc.getRunLevel() != RunLevel.RUNNING) Thread.sleep(100);
                long t = adc.getRunStartTime();
                if (t > validXTime) validXTime = t;
            }
            catch (InterruptedException intx)
            {
                logger.warn("Interrupted sleep on ADC subrun start.");
            }
        }
	    if (logger.isInfoEnabled()) {
		logger.info("Subrun time is " + validXTime);
	    }
	    return validXTime;
	}

	public void stopping()
            throws DAQCompException
	{
		try
		{
			conn.stopProcessing();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new DAQCompException("Error killing connectors", e);
			// throw new DAQCompException(e.getMessage());
		}

        logger.info("Returning from stop.");
	}

    @SuppressWarnings("unchecked")
    private void configureTrigger(String configName) throws DAQCompException {
        // Lookup the trigger configuration
        String triggerConfiguration;
        String globalConfigurationFileName = configurationPath + "/" + configName + ".xml";
        try {
            triggerConfiguration = GlobalConfiguration.getTriggerConfig(globalConfigurationFileName);
        } catch (Exception e) {
            logger.error("Error extracting trigger configuration name from global configuraion file.", e);
            throw new DAQCompException("Cannot get trigger configuration name.", e);
        }
        String triggerConfigFileName = configurationPath + "/trigger/" + triggerConfiguration + ".xml";

        // Add triggers to the trigger manager
        List currentTriggers = TriggerBuilder.buildTriggers(triggerConfigFileName, sourceId);
        Iterator triggerIter = currentTriggers.iterator();
        while (triggerIter.hasNext()) {
            ITriggerControl trigger = (ITriggerControl) triggerIter.next();
            trigger.setTriggerHandler(triggerHandler);
        }
        triggerHandler.addTriggers(currentTriggers);
    }

    /**
     * Return this component's svn version id as a String.
     *
     * @return svn version id as a String
     */
    public String getVersionInfo()
    {
		return "$Id: StringHubComponent.java 3439 2008-09-02 17:08:41Z dglo $";
    }

	public IByteBufferCache getCache()
	{
		return cache;
	}

	public DAQComponentOutputProcess getDataWriter()
	{
		return dataOut;
	}

	public DAQComponentOutputProcess getHitWriter()
	{
		return hitOut;
	}

	public int getHubId()
	{
		return hubId;
	}

	public PayloadReader getRequestReader()
	{
		return reqIn;
	}

	public Sender getSender()
	{
		return sender;
	}
}

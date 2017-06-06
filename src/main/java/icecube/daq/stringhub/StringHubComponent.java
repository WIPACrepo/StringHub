/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */
package icecube.daq.stringhub;

import icecube.daq.io.BlockingOutputEngine;
import icecube.daq.bindery.AsyncSorterOutput;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.bindery.PrioritySort;
import icecube.daq.bindery.SecondaryStreamConsumer;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.configuration.ConfigData;
import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.bindery.BufferConsumer;
import icecube.daq.bindery.ChannelSorter;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.DataCollectorFactory;
import icecube.daq.domapp.DataCollectorMBean;
import icecube.daq.domapp.MessageException;
import icecube.daq.domapp.RunLevel;
import icecube.daq.domapp.SimDataCollector;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.OutputChannel;
import icecube.daq.io.PayloadReader;
import icecube.daq.io.SimpleOutputEngine;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.mbean.MemoryStatistics;
import icecube.daq.juggler.mbean.SystemStatistics;
import icecube.daq.monitoring.DOMClockRolloverAlerter;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.monitoring.RunMonitor;
import icecube.daq.monitoring.SenderMXBean;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.ReadoutRequestFactory;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.performance.common.PowersOfTwo;
import icecube.daq.performance.diagnostic.Content;
import icecube.daq.performance.diagnostic.DataCollectorAggregateContent;
import icecube.daq.performance.diagnostic.DiagnosticTrace;
import icecube.daq.performance.diagnostic.MeterContent;
import icecube.daq.performance.diagnostic.Metered;
import icecube.daq.performance.diagnostic.SenderContent;
import icecube.daq.performance.diagnostic.cpu.CPUUtilizationContent;
import icecube.daq.priority.AdjustmentTask;
import icecube.daq.priority.SorterException;
import icecube.daq.sender.RequestReader;
import icecube.daq.sender.SenderSubsystem;
import icecube.daq.time.gps.IGPSService;
import icecube.daq.time.gps.GPSService;
import icecube.daq.time.monitoring.ClockMonitoringSubsystem;
import icecube.daq.util.DOMInfo;
import icecube.daq.util.DOMRegistryException;
import icecube.daq.util.DOMRegistryFactory;
import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.FlasherboardConfiguration;
import icecube.daq.util.JAXPUtilException;
import icecube.daq.util.StringHubAlert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

public class StringHubComponent
	extends DAQComponent
	implements StringHubComponentMBean
{
	private static final Logger logger =
		Logger.getLogger(StringHubComponent.class);

	private static final String COMPONENT_NAME =
		DAQCmdInterface.DAQ_STRING_HUB;


	private int hubId;
	private Driver driver = Driver.getInstance();
	private IByteBufferCache cache;
    private SenderSubsystem sender;
	private IDOMRegistry domRegistry;
	private IByteBufferCache moniBufMgr, tcalBufMgr, snBufMgr;
	private PayloadReader reqIn;
	private SimpleOutputEngine moniOut;
	private DAQComponentOutputProcess tcalOut;
	private DAQComponentOutputProcess supernovaOut;
	private DAQComponentOutputProcess hitOut;
	private DAQComponentOutputProcess dataOut;
	private DOMConnector conn;
	private ChannelSorter hitsSort;
	private ChannelSorter moniSort;
	private ChannelSorter tcalSort;
	private ChannelSorter scalSort;
	private File configurationPath;
	private int numConfigured;
	private IRunMonitor runMonitor;
    private DiagnosticTraceConfig trace;

	/** list of configured DOMs filled during configuring() */
	private List<DOMInfo> configuredDOMs;

	private ArrayList<PrioritySort> prioList = new ArrayList<PrioritySort>();

	private boolean forceRandom;

    /** Configurable factory for selecting the hit output engine type. */
    private OutputProcessFactory hitOutImplementation =
            OutputProcessFactory.valueOf(System.getProperty(
                    "icecube.daq.stringhub.StringHubComponent.hitOutType",
                    OutputProcessFactory.BLOCKING_8K.name()));

    /** Configurable factory for selecting the data output engine type. */
    private OutputProcessFactory dataOutImplementation =
            OutputProcessFactory.valueOf(System.getProperty(
                    "icecube.daq.stringhub.StringHubComponent.dataOutType",
                    OutputProcessFactory.BLOCKING_8K.name()));


    //todo: Remove after rhinelander release
    /**
     * Configuration directive to fall back to the legacy HitSpool/Sender
     * implementations;
     */
    public static final boolean USE_LEGACY_SENDER =
    Boolean.getBoolean("icecube.daq.sender.SenderSubsystem.use-legacy-sender");

	public StringHubComponent(int hubId)
	{
		super(COMPONENT_NAME, hubId);

		this.hubId = hubId;
	}

	public void initialize()
	{
		final boolean includeHitOut = true;
		final boolean includeReqIn = true;
		final boolean includeDataOut = true;
		final boolean includeMoniOut = true;
		final boolean includeTCalOut = true;
		final boolean includeSNOut = true;

		addMBean("jvm", new MemoryStatistics());
		addMBean("system", new SystemStatistics());
		addMBean("stringhub", this);

		/*
		 * Component derives behavioral characteristics from
		 * its 'minor ID' which is the low 3 (decimal) digits of
		 * the hub component ID:
		 *  (1) component x000        : amandaHub
		 *  (2) component x001 - x199 : in-ice hub
		 *      (79 - 86 are deep core - currently doesn't mean anything)
		 *  (3) component x200 - x299 : icetop
		 * I
		 */
		final int minorHubId = hubId % 1000;
		final int fullId = SourceIdRegistry.STRING_HUB_SOURCE_ID + minorHubId;

		final String cacheName;
		final String cacheNum;
		if (minorHubId == 0) {
			cacheName = "AM";
			cacheNum = "";
		} else if (SourceIdRegistry.isDeepCoreHubSourceID(fullId)) {
			cacheName = "DC";
			cacheNum = "#" + (minorHubId - SourceIdRegistry.DEEPCORE_ID_OFFSET);
		} else if (SourceIdRegistry.isIniceHubSourceID(fullId)) {
			cacheName = "SH";
			cacheNum = "#" + minorHubId;
		} else if (SourceIdRegistry.isIcetopHubSourceID(fullId)) {
			cacheName = "IT";
			cacheNum = "#" + (minorHubId - SourceIdRegistry.ICETOP_ID_OFFSET);
		} else {
			cacheName = "??";
			cacheNum = "#" + minorHubId;
		}

		cache  = new VitreousBufferCache(cacheName + cacheNum);
		addCache(cache);
		addMBean("PyrateBufferManager", cache);

		IByteBufferCache rdoutDataCache;
		if (!includeDataOut) {
			rdoutDataCache = null;
		} else {
			rdoutDataCache =
				new VitreousBufferCache(cacheName + "RdOut" + cacheNum);
			addCache(DAQConnector.TYPE_READOUT_DATA, rdoutDataCache);
            addMBean("readout", rdoutDataCache);

        }

        try
        {
            if(USE_LEGACY_SENDER)
            {
                sender = SenderSubsystem.Factory.STRING_HUB_COMPONENT.createLegacy(hubId,
                        cache, rdoutDataCache, domRegistry);
            }
            else
            {
                sender = SenderSubsystem.Factory.STRING_HUB_COMPONENT.create(hubId,
                        cache, rdoutDataCache, domRegistry);
            }
        }
        catch (IOException ioe)
        {
            throw new Error("Couldn't create Sender", ioe);
        }

		if (logger.isInfoEnabled()) {
			logger.info("starting up StringHub component " + hubId);
		}

		hitOut = null;

		if (minorHubId > 0) {
			// all non-AMANDA hubs send hits to a trigger
			if (includeHitOut) {
                hitOut = hitOutImplementation.create(COMPONENT_NAME, hubId,
                        "hitOut");
			}
			if (SourceIdRegistry.isIcetopHubSourceID(fullId)) {
				if (hitOut != null) {
					addMonitoredEngine(DAQConnector.TYPE_ICETOP_HIT, hitOut);
				}
			} else {
				if (hitOut != null) {
					addMonitoredEngine(DAQConnector.TYPE_STRING_HIT, hitOut);
				}
			}
			if (hitOut != null) {
				sender.setHitOutput(hitOut);
			}
		}

		if (includeReqIn) {
			ReadoutRequestFactory factory =
				new ReadoutRequestFactory(cache);
			try {
				reqIn = new RequestReader(COMPONENT_NAME,
                        sender.getReadoutRequestHandler(), factory);
			} catch (IOException ioe) {
				throw new Error("Couldn't create RequestReader", ioe);
			}
			addMonitoredEngine(DAQConnector.TYPE_READOUT_REQUEST, reqIn);
		}

		if (includeDataOut) {
			dataOut = dataOutImplementation.create(COMPONENT_NAME, hubId,
                    "dataOut");
			addMonitoredEngine(DAQConnector.TYPE_READOUT_DATA, dataOut);
            sender.setDataOutput(dataOut);
		}

        addMBean("sender", sender.getMonitor());

		// Following are the payload output engines for the secondary streams
		if (includeMoniOut) {
			moniBufMgr  = new VitreousBufferCache(cacheName + "Moni" +
												  cacheNum);
			addCache(DAQConnector.TYPE_MONI_DATA, moniBufMgr);
			moniOut = new SimpleOutputEngine(COMPONENT_NAME, hubId, "moniOut");
			addMonitoredEngine(DAQConnector.TYPE_MONI_DATA, moniOut);
		}

		if (includeTCalOut) {
			tcalBufMgr  = new VitreousBufferCache(cacheName + "TCal" +
												  cacheNum);
			addCache(DAQConnector.TYPE_TCAL_DATA, tcalBufMgr);
			tcalOut = new SimpleOutputEngine(COMPONENT_NAME, hubId, "tcalOut");
			addMonitoredEngine(DAQConnector.TYPE_TCAL_DATA, tcalOut);
		}

		if (includeSNOut) {
			snBufMgr  = new VitreousBufferCache(cacheName + "SN" + cacheNum);
			addCache(DAQConnector.TYPE_SN_DATA, snBufMgr);
			supernovaOut = new SimpleOutputEngine(COMPONENT_NAME, hubId,
												  "supernovaOut");
			addMonitoredEngine(DAQConnector.TYPE_SN_DATA, supernovaOut);
		}

		if (runMonitor != null) {
			if (runMonitor.isRunning()) {
				logger.error("Previous RunMonitor is still running!");
				runMonitor.stop();
			}
			try {
				runMonitor.join();
			} catch (InterruptedException ie) {
				logger.error("While joining with RunMonitor thread", ie);
			}
		}
		runMonitor = new RunMonitor(hubId % 1000, getAlertQueue());
		runMonitor.start();

        sender.setRunMonitor(runMonitor);

        trace = new DiagnosticTraceConfig();
	}

	List<DOMInfo> applyConfigToDOMs(ConfigData cfgData,
										Collection<DOMInfo> deployedDOMs,
										List<DOMChannelInfo> activeDOMs)
		throws DAQCompException
	{
		// Dropped DOM detection logic - WARN if channel on string AND in
		// config BUT NOT in the list of active DOMs.  Oh, and count the
		// number of channels that are active AND requested in the config
		// while we're looping

		Set<String> activeDomSet = new HashSet<String>();
		for (DOMChannelInfo chanInfo : activeDOMs)
		{
			activeDomSet.add(chanInfo.mbid);
			if (cfgData.isDOMIncluded(chanInfo.mbid)) {
				numConfigured++;
			}
		}

		if (numConfigured == 0) {
			throw new DAQCompException("No Active DOMs on Hub " + hubId +
									   " selected in configuration.");
		}

		// check all the DOMs which are known to be on this hub
		ArrayList<DOMInfo> doms = new ArrayList<DOMInfo>();
		for (DOMInfo deployedDOM : deployedDOMs) {
			String mbid = deployedDOM.getMainboardId();

			// if this DOM is in the run configuration...
			if (cfgData.isDOMIncluded(mbid)) {

				// complain about DOMs which are in the config
				// but are not active
				if (!activeDomSet.contains(mbid)) {
					logger.warn("DOM " + deployedDOM +
								" requested in configuration for hub " +
								hubId + " but not found.");

					StringHubAlert.
						sendDOMAlert(getAlertQueue(),
									 Alerter.Priority.EMAIL,
									 "Dropped DOM",
									 StringHubAlert.NO_CARD,
									 StringHubAlert.NO_PAIR,
									 StringHubAlert.NO_SPECIFIER,
									 deployedDOM.getMainboardId(),
									 deployedDOM.getName(),
									 deployedDOM.getStringMajor(),
									 deployedDOM.getStringMinor(),
									 StringHubAlert.NO_RUNNUMBER,
									 StringHubAlert.NO_UTCTIME);
				}

				// add to the list of configured DOMs
				doms.add(deployedDOM);
			}
		}

		new DOMSorter().sort(doms);

		return doms;
	}

	@Override
	public void setGlobalConfigurationDir(String dirName)
	{
		configurationPath = new File(dirName);
		if (!configurationPath.exists()) {
			throw new Error("Configuration directory \"" + configurationPath +
							"\" does not exist");
		}

		if (logger.isInfoEnabled()) {
			logger.info("Setting the ueber configuration directory to " +
						configurationPath);
		}
		// get a reference to the DOM registry - useful later
		try {
			domRegistry = DOMRegistryFactory.load(configurationPath);
		} catch (DOMRegistryException dre) {
			logger.error("Could not load DOMRegistry", dre);
		}
    }

    @Override
    /**
     * Extends startup to include the clock monitoring
     */
    public void start() throws DAQCompException
    {
        super.start();

        //start up the clock monitoring subsystem
        final Object mbean =
                ClockMonitoringSubsystem.Factory.subsystem().startup(getAlertQueue());
        if (mbean != null) {
            addMBean("ClockMonitor", mbean);
        }
    }

    /**
     * Terminate string hub services.
     *
     * todo: In the current implementation, StringHub components are not
     *       terminated gracefully by the controller. Non-Daemon threads
     *       do not prevent component/jvm shutdown.  If this changes, this
     *       method should be wired into the shutdown. Until then, this
     *       method serves as reference code for non-run-related resources
     *       that should be cleaned up.
     */
    private void terminate()
    {
        GPSService.getInstance().shutdownAll();
        ClockMonitoringSubsystem.Factory.subsystem().shutdown();
    }

	/**
	 * Close all open files, sockets, etc.
	 *
	 * @throws IOException if there is a problem
	 */
	public void closeAll()
		throws IOException
	{
		moniOut.destroyProcessor();
		tcalOut.destroyProcessor();
		supernovaOut.destroyProcessor();
		hitOut.destroyProcessor();
		reqIn.destroyProcessor();
		dataOut.destroyProcessor();

		super.closeAll();
	}

	/**
	 * Query the driver for a list of DOMs. For a DOM to be detected its
	 * cardX/pairY/domZ/id procfile must report a valid non-zero DOM
	 * mainboard ID.
	 *
	 * @return list of DOMChannelInfo entries
	 */
	private List<DOMChannelInfo> discoverRealDOMs()
		throws DAQCompException
	{
		try {
			driver.setBlocking(true);
			return driver.discoverActiveDOMs();
		} catch (IOException io) {
			logger.error("Cannot discover DOMs on hub " + hubId, io);
			throw new DAQCompException("Cannot discover hub " + hubId, io);
		} catch (Throwable t) {
			throw new DAQCompException("Unexpected hub " + hubId +
									   " exception", t);
		}
	}

	/**
	 * Build a list of all possible DOMs for the string
	 *
	 * @return list of DOMChannelInfo entries
	 */
	private List<DOMChannelInfo> discoverSimDOMs(Collection<DOMInfo>
												 attachedDOMs)
	{
		List<DOMChannelInfo> activeDOMs =
			new ArrayList<DOMChannelInfo>(attachedDOMs.size());
		for (DOMInfo dom : attachedDOMs) {
			int card = (dom.getStringMinor()-1) / 8;
			int pair = ((dom.getStringMinor()-1) % 8) / 2;
			char aorb = 'A';
			// not a major change, but the previous code here used
			// % 2 == 1 to check for oddness.  YES, the string's are all
			// positive integers, but that test would fail for negative
			// numbers.
			if (dom.getStringMinor() % 2 != 0) aorb = 'B';
			activeDOMs.add(new DOMChannelInfo(dom.getMainboardId(),
											  dom.getNumericMainboardId(),
											  card, pair, aorb));
		}
		return activeDOMs;
	}

	/**
	 * StringHub responds to a configure request from the controller
	 */
	@SuppressWarnings("unchecked")
	public void configuring(String configName) throws DAQCompException
	{
		configure(configName, true);
	}

	public void configure(String configName, boolean openSecondary)
		throws DAQCompException
	{
		if (configurationPath == null) {
			throw new DAQCompException("Global configuration directory" +
									   " has not been set");
		}

		Collection<DOMInfo> deployedDOMs;
		try {
			deployedDOMs = domRegistry.getDomsOnHub(hubId);
		} catch (DOMRegistryException dre) {
			throw new DAQCompException("Cannot get DOMs on hub " + hubId, dre);
		}

		ConfigData cfgData;
		try {
			cfgData = new ConfigData(configurationPath, configName, hubId,
									 deployedDOMs);
		} catch (JAXPUtilException jux) {
			throw new DAQCompException(jux);
		}

		final boolean isSim = cfgData.isRandom || forceRandom ||
			(hubId >= 1000 && hubId < 2000);

		// Lookup the connected DOMs
		List<DOMChannelInfo> activeDOMs;
		if (isSim) {
			activeDOMs = discoverSimDOMs(deployedDOMs);
		} else {
			activeDOMs = discoverRealDOMs();

			// GPS service needs string number for monitoring messages
			IGPSService inst = GPSService.getInstance();
			inst.setStringNumber(hubId % 1000);
		}

		if (activeDOMs.size() == 0) {
			throw new DAQCompException("No Active DOMs on hub " + hubId);
		}

		if (cfgData.forwardIsolatedHits) {
			sender.forwardIsolatedHitsToTrigger();
		}

		configuredDOMs = applyConfigToDOMs(cfgData, deployedDOMs, activeDOMs);
		runMonitor.setConfiguredDOMs(configuredDOMs);

		logger.debug("Configuration successfully loaded -" +
					 " Intersection(DISC, CONFIG).size() = " + numConfigured);

		configOutput(cfgData, openSecondary);

		createDataCollectors(cfgData, activeDOMs, isSim);

		// Still need to get the data collectors to pick up
		// and do something with the config
		try {
			conn.configure();
		} catch (InterruptedException ie) {
			throw new DAQCompException("Interrupted while waiting for DOMs" +
									   " to finish configuring");
		}
	}

	/**
	 * Set up the output objects for physics, monitoring, supernova and
	 * time calibration streams
	 *
	 * @param cfgData configuration data
	 * @param openSecondary if <tt>true</tt> open the secondary streams
	 *
	 * @throws DAQCompException if there is a problem
	 */
	private void configOutput(ConfigData cfgData, boolean openSecondary)
		throws DAQCompException
	{
		// Must make sure to release file resources associated with the
		// previous runs since we are throwing away the collectors and
		// starting from scratch
		if (conn != null) {
			try {
				conn.destroy();
			} catch (InterruptedException ie) {
				throw new DAQCompException("Cannot destroy previous" +
										   " connector for Hub " + hubId, ie);
			}
		}

		conn = new DOMConnector(numConfigured);

		SecondaryStreamConsumer monitorConsumer;
		SecondaryStreamConsumer supernovaConsumer;
		SecondaryStreamConsumer tcalConsumer;
		if (!openSecondary) {
			monitorConsumer = null;
			supernovaConsumer = null;
			tcalConsumer = null;
		} else {
			monitorConsumer =
				new SecondaryStreamConsumer(hubId, moniBufMgr,
											moniOut.getChannel());
			supernovaConsumer =
				new SecondaryStreamConsumer(hubId, snBufMgr,
											supernovaOut.getChannel());
			tcalConsumer =
				new SecondaryStreamConsumer(hubId, tcalBufMgr,
											tcalOut.getChannel(),
											cfgData.tcalPrescale);

            // Support tcal capture, a non-standard diagnostic
            TimeCalibrationCaptureSubsystem.activate(tcalConsumer);
		}

		// the hit buffer consumer is either the sender or a hitspool
		// object which passes all hits onto the sender
        BufferConsumer consumer = new AsyncSorterOutput(sender.getHitInput(),
                PowersOfTwo._2097152, "hit-consumer",
                trace.getAsyncHitConsumerMeter());

		final boolean usePriority =
			System.getProperty("usePrioritySort") != null;

		// Start the hit merger-sorter
		if (!usePriority) {
			hitsSort = new MultiChannelMergeSort(numConfigured, consumer,
                    "hit", trace.getSortQueueMeter(), trace.getSortMeter());
		} else {
			PrioritySort tmp;
			try {
				tmp = new PrioritySort("HitsSort", numConfigured, consumer);
			} catch (SorterException se) {
				throw new DAQCompException("Cannot create hit sorter", se);
			}

			prioList.add(tmp);
			hitsSort = tmp;
		}

		// start remaining merger-sorter objects
		if (usePriority) {
			PrioritySort tmp;

			try {
				tmp = new PrioritySort("MoniSort", numConfigured,
									   monitorConsumer);
				prioList.add(tmp);
				moniSort = tmp;

				tmp = new PrioritySort("SNSort", numConfigured,
									   supernovaConsumer);
				prioList.add(tmp);
				scalSort = tmp;

				tmp = new PrioritySort("TCalSort", numConfigured, tcalConsumer);
				prioList.add(tmp);
				tcalSort = tmp;
			} catch (SorterException se) {
				throw new DAQCompException("Cannot create sorter", se);
			}
		} else {
			moniSort = new MultiChannelMergeSort(numConfigured, monitorConsumer);
			scalSort = new MultiChannelMergeSort(numConfigured,
												 supernovaConsumer);
			tcalSort = new MultiChannelMergeSort(numConfigured, tcalConsumer);
		}

		if (prioList.size() > 0) {
			// monitor all PrioritySort objects
			for (PrioritySort ps : prioList) {
				addMBean(ps.getName(), ps);
			}
		}
	}

	private AbstractDataCollector
		createDataCollector(boolean isSim, DOMChannelInfo chanInfo,
							DOMConfiguration config,
							ChannelSorter hitsSort,
							ChannelSorter moniSort,
							ChannelSorter tcalSort,
							ChannelSorter scalSort,
							boolean enable_intervals, double snDistance)
		throws IOException, MessageException
	{
		if (!isSim) {
            DataCollector dc =
                    DataCollectorFactory.buildDataCollector(chanInfo.card, chanInfo.pair, chanInfo.dom,
								  chanInfo.mbid, config, hitsSort, moniSort,
								  scalSort, tcalSort, enable_intervals);
			addMBean("DataCollectorMonitor-" + chanInfo, dc);
			dc.setRunMonitor(runMonitor);
			return dc;
		}

		if (!Double.isNaN(snDistance)) {
			config.setSnSigEnabled(true);
			config.setSnDistance(snDistance);
			logger.debug("SN Distance "+ snDistance);
		}

		final boolean isAmanda = (getNumber() % 1000) == 0;
		return new SimDataCollector(chanInfo, config, hitsSort, moniSort,
									scalSort, tcalSort, isAmanda);
	}

	private void createDataCollectors(ConfigData cfgData,
									  List<DOMChannelInfo> activeDOMs,
									  boolean isSim)
		throws DAQCompException
	{
		for (DOMChannelInfo chanInfo : activeDOMs) {
			DOMConfiguration config = cfgData.getDOMConfig(chanInfo.mbid);
			if (config == null) continue;

			String cwd = chanInfo.card + "" + chanInfo.pair + chanInfo.dom;

			DOMInfo domInfo = domRegistry.getDom(chanInfo.mbid_numerique);

			// Associate a GPS service to this card, if not already done
			if (!isSim) {
				IGPSService inst = GPSService.getInstance();
				inst.setRunMonitor(runMonitor);
				inst.startService(chanInfo.card);
			}

			AbstractDataCollector dc;
			try {
				dc = createDataCollector(isSim, chanInfo, config, hitsSort,
										 moniSort, tcalSort, scalSort,
										 cfgData.enable_intervals,
										 cfgData.snDistance);
			} catch (Throwable t) {
				throw new DAQCompException("Cannot create " + hubId +
										   " data collector", t);
			}

			dc.setDomInfo(domInfo);

			dc.setSoftbootBehavior(cfgData.dcSoftboot);
			hitsSort.register(chanInfo.mbid_numerique);
			moniSort.register(chanInfo.mbid_numerique);
			scalSort.register(chanInfo.mbid_numerique);
			tcalSort.register(chanInfo.mbid_numerique);
			dc.setAlertQueue(getAlertQueue());
			dc.setRunMonitor(runMonitor);
			conn.add(dc);
			if (logger.isDebugEnabled()) {
				logger.debug("Starting new DataCollector thread on (" + cwd +
							 ").");
			}
		}
    }



	/**
	 * Used for testing StringHub without real DOMs
	 */
	public void forceRandomMode()
	{
		forceRandom = true;
	}

	/**
	 * Send the list of configured DOMs for this hub.
	 *
	 * @param runNumber run number
	 *
	 * @throws DAQCompException if the alert cannot be sent
	 */
	private void sendConfiguredDOMs(int runNumber)
		throws DAQCompException
	{
		AlertQueue alertQueue = getAlertQueue();
		if (alertQueue.isStopped()) {
			throw new DAQCompException("AlertQueue " + alertQueue +
									   " is stopped");
		}

		if (configuredDOMs == null || configuredDOMs.size() == 0) {
			throw new DAQCompException("No DOMs have been configured");
		}

		if (getNumber() % 1000 < SourceIdRegistry.ICETOP_ID_OFFSET) {
			sendConfiguredIniceDOMs(runNumber, alertQueue);
		} else {
			sendConfiguredIcetopDOMs(runNumber, alertQueue);
		}
	}

	/**
	 * Send the list(s) of configured icetop DOMs
	 *
	 * @param runNumber run number
	 * @param alertQueue alert sender
	 *
	 * @throws DAQCompException if the alert cannot be sent
	 */
	private void sendConfiguredIcetopDOMs(int runNumber, AlertQueue alertQueue)
		throws DAQCompException
	{
		int strnum = -1;
		int first = 0;
		for (int i = 0; i < configuredDOMs.size(); i++) {
			DOMInfo dom = configuredDOMs.get(i);
			if (dom.getStringMajor() == strnum) {
				// this DOM is on the same string
				continue;
			}

			if (strnum > 0) {
				sendOneIcetopReport(runNumber, alertQueue, strnum, first,
									i - first);
			}

			strnum = dom.getStringMajor();
			first = i;
		}

		if (strnum > 0) {
			sendOneIcetopReport(runNumber, alertQueue, strnum, first,
								configuredDOMs.size() - first);
		}
	}

	/**
	 * Send the list of configured in-ice DOMs for this hub.
	 *
	 * @param runNumber run number
	 *
	 * @throws DAQCompException if the alert cannot be sent
	 */
	private void sendConfiguredIniceDOMs(int runNumber, AlertQueue alertQueue)
		throws DAQCompException
	{
		int[] list = new int[configuredDOMs.size()];

		int idx = 0;
		for (DOMInfo dom : configuredDOMs) {
			list[idx++] = dom.getStringMinor();
		}

		HashMap<String, Object> values = new HashMap<String, Object>();
		values.put("string", getNumber());
		values.put("runNumber", runNumber);
		values.put("doms", list);

		try {
			alertQueue.push("doms_in_config", Alerter.Priority.EMAIL, values);
		} catch (AlertException ae) {
			throw new DAQCompException("Cannot send alert", ae);
		}
	}

	/**
	 * Send a single list of configured icetop DOMs for the specified tank
	 *
	 * @param runNumber run number
	 * @param alertQueue alert sender
	 * @param strnum original string number
	 * @param first first index into configuredDOMs
	 * @param len number of DOMs in list
	 *
	 * @throws DAQCompException if the alert cannot be sent
	 */
	private void sendOneIcetopReport(int runNumber, AlertQueue alertQueue,
									 int strnum, int first, int len)
		throws DAQCompException
	{
		int[] list = new int[len];
		for (int i = 0; i < len; i++) {
			list[i] = configuredDOMs.get(first + i).getStringMinor();
		}

		HashMap<String, Object> values = new HashMap<String, Object>();
		values.put("string", strnum);
		values.put("runNumber", runNumber);
		values.put("doms", list);

		try {
			alertQueue.push("doms_in_config", Alerter.Priority.EMAIL,
							values);
		} catch (AlertException ae) {
			throw new DAQCompException("Cannot send alert", ae);
		}
	}

    /**
     * Collect DOM clock values and examine for rollover conditions.
     */
    private void checkDOMClocks()
    {
        Map<DOMChannelInfo, Long> records = new HashMap<DOMChannelInfo, Long>();
        for (AbstractDataCollector dc : conn.getCollectors())
        {
            if(dc instanceof DataCollector)
            {
                DataCollector narrow = (DataCollector)dc;
                DOMChannelInfo details =
                        new DOMChannelInfo(dc.getMainboardId(),
                                           dc.getCard(),
                                           dc.getPair(),
                                           dc.getDom());
                narrow.getFirstDOMTime();
                records.put(details, narrow.getFirstDOMTime());
            }
        }

        DOMClockRolloverAlerter checker = new DOMClockRolloverAlerter();

        try
        {
            checker.monitorDOMClocks(getAlertQueue(), records);
        }
        catch (AlertException e)
        {
            logger.error("Unable to check DOM clocks for pending rollover", e);
        }

    }

	/**
     * Set the run number inside this component.
     *
     * @param runNumber run number
     */
	public void setRunNumber(int runNumber)
	{
		logger.info("Set run number");
		if (conn == null) {
			logger.error("DOMConnector has not been initialized!");
		} else {
			for (AbstractDataCollector adc : conn.getCollectors()) {
				adc.setRunNumber(runNumber);
			}
		}
		if (runMonitor == null) {
			logger.error("RunMonitor has not been initialized!");
		} else {
			runMonitor.setRunNumber(runNumber);
		}
	}

	/**
	 * Controller wants StringHub to start sending data.
	 * Tell DOMs to start up.
	 */
	public void starting(int runNumber)
		throws DAQCompException
	{
        trace.startTrace(trace.narrowCollectors(conn.getCollectors()),
                sender.getMonitor());

        setRunNumber(runNumber);

		logger.info("StringHub is starting the run.");

		sender.startup();

		hitsSort.start();
		moniSort.start();
		scalSort.start();
		tcalSort.start();

		if (prioList.size() > 0) {
			// start adjustment thread for priority sorters
			AdjustmentTask task = new AdjustmentTask();
			for (PrioritySort ps : prioList) {
				ps.registerSorter(task);
			}
			task.start();
		}

		try
		{
			conn.startProcessing();
		}
		catch (Exception e)
		{
			throw new DAQCompException("Couldn't start DOMs", e);
		}

		AlertQueue alertQueue = getAlertQueue();
		if (alertQueue.isStopped()) {
			alertQueue.start();
		}

		// resend the list of this hub's DOMs which are in the run config
		sendConfiguredDOMs(runNumber);

        // check DOM clocks for pending rollovers
        checkDOMClocks();
	}

	public long startSubrun(List<FlasherboardConfiguration> flasherConfigs)
		throws DAQCompException
	{
		/*
		 * Useful to keep operators from accidentally powering up two
		 * flasherboards simultaneously.
		 */
		boolean[] wirePairSemaphore = new boolean[32];
		long validXTime = 0L;

		/* Load the configs into a map so that I can search them better */
		HashMap<String, FlasherboardConfiguration> fcMap =
			new HashMap<String, FlasherboardConfiguration>(60);
		for (FlasherboardConfiguration fb : flasherConfigs)
			fcMap.put(fb.getMainboardID(), fb);

		/*
		 * Divide the DOMs into 4 categories ...
		 *     Category 1: Flashing current subrun - not flashing next subrun.
		 *                 Simply turn these DOMs' flashers off - this must
		 *                 be done over all DOMs in first pass to ensure that
		 *                 DOMs on the same wire pair are never simultaneously
		 *                 on (it blows the DOR card firmware fuse).
		 *     Category 2: Flashing current subrun - flashing with new config
		 *                 next subrun. These DOMs must get the CHANGE_FLASHER
		 *                 signal (new feature added DOM-MB 437+)
		 *     Category 3: Not flashing current subrun - flashing next subrun.
		 *                 These DOMs get a START_FLASHER_RUN signal
		 *     Category 4: Others
		 */

		logger.info("Beginning subrun - turning off requested flashers");
		for (AbstractDataCollector adc : conn.getCollectors())
		{
			if (adc.isRunning()
				&& adc.getFlasherConfig() != null
				&& !fcMap.containsKey(adc.getMainboardId()))
			{
				adc.setFlasherConfig(null);
				adc.signalStartSubRun();
			}
		}

		for (AbstractDataCollector adc : conn.getCollectors())
		{
			if (adc.isZombie()) continue;
			try
			{
				while (!adc.isRunning()) Thread.sleep(100);
			}
			catch (InterruptedException intx)
			{
				logger.warn("Interrupted sleep on ADC subrun start.");
			}
		}

		logger.info("Turning on / changing flasher configs for next subrun");
		for (AbstractDataCollector adc : conn.getCollectors())
		{
			String mbid = adc.getMainboardId();
			if (fcMap.containsKey(mbid))
			{
				int pairIndex = 4 * adc.getCard() + adc.getPair();
				if (wirePairSemaphore[pairIndex])
					throw new DAQCompException("Cannot activate > 1 flasher" +
											   " run per DOR wire pair.");
				wirePairSemaphore[pairIndex] = true;
				adc.setFlasherConfig(fcMap.get(mbid));
				adc.signalStartSubRun();
			}
		}

		for (AbstractDataCollector adc : conn.getCollectors())
		{
			if (adc.getRunLevel() == RunLevel.ZOMBIE) continue;
			try
			{
				while (adc.getRunLevel() != RunLevel.RUNNING)
					Thread.sleep(100);
				long t = adc.getRunStartTime();
				if (t > validXTime) validXTime = t;
			}
			catch (InterruptedException intx)
			{
				logger.warn("Interrupted sleep on ADC subrun start.");
			}
		}

		logger.info("Subrun time is " + validXTime);
		return validXTime;
	}

	public void stopping()
		throws DAQCompException
	{
		logger.info("Entering run stop handler");

		try
		{
			conn.stopProcessing();
		}
		catch (Exception e)
		{
			throw new DAQCompException("Error killing connectors", e);
			// throw new DAQCompException(e.getMessage());
		}

		DAQComponentOutputProcess[] eng = new DAQComponentOutputProcess[] {
			moniOut, supernovaOut, tcalOut
		};

		for (int i = 0; i < eng.length; i++) {
			OutputChannel chan = eng[i].getChannel();
			if (chan != null) {
				chan.sendLastAndStop();
			}
		}

		if (runMonitor != null) {
			runMonitor.stop();
		}

        trace.stopTrace();

		logger.info("Returning from stop.");
	}

	/**
	 * Perform any actions related to switching to a new run.
	 *
	 * @param runNumber new run number
	 *
	 * @throws DAQCompException if there is a problem switching the component
	 */
	public void switching(int runNumber)
		throws DAQCompException
	{
		// this may set the run number before it actually starts,
		// but hubs have no other way of knowing when the new number has begun
		setRunNumber(runNumber);

		// resend the list of this hub's DOMs which are in the run config
		sendConfiguredDOMs(runNumber);
	}

	/**
	 * Return this component's svn version id as a String.
	 *
	 * @return svn version id as a String
	 */
	public String getVersionInfo()
	{
		return "$Id: StringHubComponent.java 16585 2017-06-06 17:38:25Z bendfelt $";
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

	public SenderMXBean getSenderMonitor()
	{
		return sender.getMonitor();
	}

    public SenderSubsystem getSender()
    {
        return sender;
    }

	public int getNumberOfActiveChannels()
	{
		int nch = 0;
		for (AbstractDataCollector adc : conn.getCollectors()) {
			if (adc.isRunning()) {
				nch++;
			}
		}
		return nch;
	}

	public int[] getNumberOfActiveAndTotalChannels() {
		int nch = 0;
		int total = 0;
		for (AbstractDataCollector adc : conn.getCollectors()) {
			if(adc.isRunning()) nch++;
			total++;
		}

		int[] returnVal = new int[2];
		returnVal[0] = nch;
		returnVal[1] = total;

		return returnVal;
	}


	public double getHitRate() {
		double total = 0.;

		for (AbstractDataCollector adc : conn.getCollectors()) {
			total += adc.getHitRate();
		}

		return total;
	}

	public double getHitRateLC() {
		double total = 0.;

		for (AbstractDataCollector adc : conn.getCollectors()) {
			total += adc.getHitRateLC();
		}

		return total;
	}

	public long getTotalLBMOverflows() {
		long total = 0;

		for (AbstractDataCollector adc : conn.getCollectors()) {
			total += adc.getLBMOverflowCount();
		}

		return total;
	}

	public long getTimeOfLastHitInputToHKN1()
	{
		if (hitsSort == null) return 0L;
		return hitsSort.getLastInputTime();
	}

	public long getTimeOfLastHitOutputFromHKN1()
	{
		if (hitsSort == null) return 0L;
		return hitsSort.getLastOutputTime();
	}

	public int getNumberOfNonZombies()
	{
		int num = 0;
		for (AbstractDataCollector adc : conn.getCollectors()) {
			if (!adc.isZombie()) {
				num++;
			}
		}
		return num;
	}

	public long getLatestFirstChannelHitTime()
	{
		GoodTimeCalculator gtc = new GoodTimeCalculator(conn, true);
		return gtc.getTime();
	}

	public long getEarliestLastChannelHitTime()
	{
		GoodTimeCalculator gtc = new GoodTimeCalculator(conn, false);
		return gtc.getTime();
	}

	class DOMSorter
		implements Comparator<DOMInfo>
	{
		/**
		 * Compare two DOMs.
		 *
		 * @param d1 first DOM
		 * @param d2 second DOM
		 *
		 * @return the usual comparison values
		 */
		public int compare(DOMInfo d1, DOMInfo d2)
		{
			int val = d1.getStringMajor() - d2.getStringMajor();
			if (val == 0) {
				val = d2.getStringMinor() - d2.getStringMinor();
			}
			return (val == 0 ? 0 : (val < 0 ? -1 : 1));
		}

		/**
		 * Do the objects implement the same class?
		 *
		 * @return <tt>true</tt> if they are the same class
		 */
		public boolean equals(Object obj)
		{
			return obj.getClass().getName().equals(getClass().getName());
		}

		/**
		 * Sort the list of doms.
		 *
		 * @param doms list of doms
		 */
		public void sort(List<DOMInfo> doms)
		{
			Collections.sort(doms, this);
		}
	}

    /**
     * Encapsulates the optional injection of a performance trace into the
     * hit processing stack.
     */
    private static class DiagnosticTraceConfig
    {

        private static boolean enabled =
                Boolean.getBoolean("icecube.daq.stringhub.trace.enabled");

        private static int period =
                Integer.getInteger("icecube.daq.stringhub.trace.period", 1000);

        private static String file =
                System.getProperty("icecube.daq.stringhub.trace.file",
                        "/dev/null");

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

        private void startTrace(final List<DataCollectorMBean> collectors,
                                final SenderMXBean sender)
        {
            try
            {
                if(enabled)
                {
                    FileOutputStream fos = new FileOutputStream(file);

                    trace = new DiagnosticTrace(period, 30,
                            new PrintStream(fos));
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

                    trace.addContent(new Content.GroupedContent("sender", new SenderContent(sender)));

                    CPUUtilizationContent cpu = new CPUUtilizationContent(10000/period);
                    trace.addFlyWeight(cpu);
                    trace.addContent(cpu.createSystemUtilizationContent());
                    trace.addContent(cpu.createProcessUtilizationContent());

                    trace.start();
                }
            }
            catch (FileNotFoundException e)
            {
                logger.warn("Can not start trace ", e);
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

        private List<DataCollectorMBean> narrowCollectors(
                final List<AbstractDataCollector> collectors)
        {
            final List<DataCollectorMBean> narrow =
                    new ArrayList<>(collectors.size());

            for(AbstractDataCollector dc : collectors)
            {
                if(dc instanceof  DataCollectorMBean)
                {
                    narrow.add((DataCollectorMBean) dc);
                }
            }

            return narrow;
        }
    }


    /**
     * An enumerated factory for output engine selection.
     */
    private enum OutputProcessFactory
    {
        SIMPLE
                {
                    @Override
                    DAQComponentOutputProcess create(final String componentName,
                                                     final int hubId,
                                                     final String type)
                    {
                        return new SimpleOutputEngine(componentName,
                                hubId, type);
                    }
                },
        BLOCKING_0K
                {
                    @Override
                    DAQComponentOutputProcess create(final String componentName,
                                                     final int hubId,
                                                     final String type)
                    {
                        return new BlockingOutputEngine(0);
                    }
                },
        BLOCKING_1K
                {
                    @Override
                    DAQComponentOutputProcess create(final String componentName,
                                                     final int hubId,
                                                     final String type)
                    {
                        return new BlockingOutputEngine(1024, true,
                                AUTOFLUSH_PERIOD);
                    }
                },
        BLOCKING_2K
                {
                    @Override
                    DAQComponentOutputProcess create(final String componentName,
                                                     final int hubId,
                                                     final String type)
                    {
                        return new BlockingOutputEngine(2 * 1024, true,
                                AUTOFLUSH_PERIOD);
                    }
                },
        BLOCKING_4K
                {
                    @Override
                    DAQComponentOutputProcess create(final String componentName,
                                                     final int hubId,
                                                     final String type)
                    {
                        return new BlockingOutputEngine(4 * 1024, true,
                                AUTOFLUSH_PERIOD);
                    }
                },
        BLOCKING_8K
                {
                    @Override
                    DAQComponentOutputProcess create(final String componentName,
                                                     final int hubId,
                                                     final String type)
                    {
                        return new BlockingOutputEngine(8 * 1024, true,
                                AUTOFLUSH_PERIOD);
                    }
                },
        BLOCKING_32K
                {
                    @Override
                    DAQComponentOutputProcess create(final String componentName,
                                                     final int hubId,
                                                     final String type)
                    {
                        return new BlockingOutputEngine(32 * 1024, true,
                                AUTOFLUSH_PERIOD);
                    }
                },
        BLOCKING_64k /** NOTE: likely cause watchdog starvation.*/
                {
                    @Override
                    DAQComponentOutputProcess create(final String componentName,
                                                     final int hubId,
                                                     final String type)
                    {
                        return new BlockingOutputEngine(64 * 1024, true,
                                AUTOFLUSH_PERIOD);
                    }
                },
        BLOCKING_128K /** NOTE: likely cause watchdog starvation.*/
                {
                    @Override
                    DAQComponentOutputProcess create(final String componentName,
                                                     final int hubId,
                                                     final String type)
                    {
                        return new BlockingOutputEngine(128 * 1024, true,
                                AUTOFLUSH_PERIOD);
                    }
                };

        abstract DAQComponentOutputProcess create(String componentName,
                                                      int hubId,
                                                      String type);

        /**
         * The autoflush period in millis for buffered output channels.
         * Chosen to satisfy the watchdog in the event of a channel with
         * a very tiny data rate.
         */
        private static String AUTOFLUSH_PROPERTY =
           "icecube.daq.stringhub.StringHubComponent.output.autoflush-millis";
        private static long AUTOFLUSH_PERIOD =
                Long.getLong(AUTOFLUSH_PROPERTY, 5000);

    }

}

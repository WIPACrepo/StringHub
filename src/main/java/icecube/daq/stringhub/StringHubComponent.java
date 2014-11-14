/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */
package icecube.daq.stringhub;

import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.bindery.SecondaryStreamConsumer;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.configuration.XMLConfig;
import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.MessageException;
import icecube.daq.domapp.RunLevel;
import icecube.daq.domapp.SimDataCollector;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSService;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.OutputChannel;
import icecube.daq.io.PayloadReader;
import icecube.daq.io.SimpleOutputEngine;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.mbean.MemoryStatistics;
import icecube.daq.juggler.mbean.SystemStatistics;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.monitoring.MonitoringData;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.ReadoutRequestFactory;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.sender.RequestReader;
import icecube.daq.sender.Sender;
import icecube.daq.util.DOMRegistry;
import icecube.daq.util.DeployedDOM;
import icecube.daq.util.FlasherboardConfiguration;
import icecube.daq.util.JAXPUtil;
import icecube.daq.util.JAXPUtilException;
import icecube.daq.util.StringHubAlert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

public class StringHubComponent
	extends DAQComponent
	implements StringHubComponentMBean
{

	private static final Logger logger =
		Logger.getLogger(StringHubComponent.class);

	private static final String COMPONENT_NAME =
		DAQCmdInterface.DAQ_STRING_HUB;

	private int hubId;
	private boolean isSim;
	private Driver driver = Driver.getInstance();
	private IByteBufferCache cache;
	private Sender sender;
	private DOMRegistry domRegistry;
	private IByteBufferCache moniBufMgr, tcalBufMgr, snBufMgr;
	private PayloadReader reqIn;
	private SimpleOutputEngine moniOut;
	private SimpleOutputEngine tcalOut;
	private SimpleOutputEngine supernovaOut;
	private SimpleOutputEngine hitOut;
	private SimpleOutputEngine teOut;
	private SimpleOutputEngine dataOut;
	private DOMConnector conn;
	private MultiChannelMergeSort hitsSort;
	private MultiChannelMergeSort moniSort;
	private MultiChannelMergeSort tcalSort;
	private MultiChannelMergeSort scalSort;
	private File configurationPath;

	private int runNumber;

	private boolean hitSpooling;
	private String hitSpoolDir;
	private long hitSpoolIval;

	private int hitSpoolNumFiles = 100;

	public StringHubComponent(int hubId)
	{
		this(hubId, (hubId >= 1000 && hubId < 2000));
	}

	public StringHubComponent(int hubId, boolean isSim)
	{
		this(hubId, isSim, true, true, true, true, true, true, true);
	}

	public StringHubComponent(int hubId, boolean isSim, boolean includeHitOut,
							  boolean includeTEOut, boolean includeReqIn,
							  boolean includeDataOut, boolean includeMoniOut,
							  boolean includeTCalOut, boolean includeSNOut)
	{
		super(COMPONENT_NAME, hubId);

		this.hubId = hubId;
		this.isSim = isSim;

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
		}

		sender = new Sender(hubId, rdoutDataCache);

		if (logger.isInfoEnabled()) {
			logger.info("starting up StringHub component " + hubId);
		}

		hitOut = null;
		teOut = null;

		if (minorHubId > 0) {
			// all non-AMANDA hubs send hits to a trigger
			if (includeHitOut) {
				hitOut = new SimpleOutputEngine(COMPONENT_NAME, hubId,
												"hitOut");
			}
			if (includeTEOut) {
				teOut = new SimpleOutputEngine(COMPONENT_NAME, hubId, "teOut",
											   true);
			}
			if (SourceIdRegistry.isIcetopHubSourceID(fullId)) {
				if (hitOut != null) {
					addMonitoredEngine(DAQConnector.TYPE_ICETOP_HIT, hitOut);
				}
			} else {
				if (hitOut != null) {
					addMonitoredEngine(DAQConnector.TYPE_STRING_HIT, hitOut);
				}
				if (teOut != null) {
					addOptionalEngine(DAQConnector.TYPE_TRACKENG_HIT, teOut);
				}
			}
			if (hitOut != null) {
				sender.setHitOutput(hitOut);
				sender.setHitCache(cache);
			}
			if (teOut != null) {
				sender.setTrackEngineOutput(teOut);
				sender.setTrackEngineCache(cache);
			}
		}

		if (includeReqIn) {
			ReadoutRequestFactory factory =
				new ReadoutRequestFactory(cache);
			try {
				reqIn = new RequestReader(COMPONENT_NAME, sender, factory);
			} catch (IOException ioe) {
				throw new Error("Couldn't create RequestReader", ioe);
			}
			addMonitoredEngine(DAQConnector.TYPE_READOUT_REQUEST, reqIn);
		}

		if (includeDataOut) {
			dataOut =
				new SimpleOutputEngine(COMPONENT_NAME, hubId, "dataOut");
			addMonitoredEngine(DAQConnector.TYPE_READOUT_DATA, dataOut);
			sender.setDataOutput(dataOut);
		}

		MonitoringData monData = new MonitoringData();
		monData.setSenderMonitor(sender);
		addMBean("sender", monData);

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

		// Default 10s hit spool interval
		hitSpoolIval = 100000000000L;
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
			domRegistry = DOMRegistry.loadRegistry(configurationPath);
		} catch (ParserConfigurationException e) {
			logger.error("Could not load DOMRegistry", e);
		} catch (SAXException e) {
			logger.error("Could not load DOMRegistry", e);
		} catch (IOException e) {
			logger.error("Could not load DOMRegistry", e);
		}

		sender.setDOMRegistry(domRegistry);
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
		teOut.destroyProcessor();
		reqIn.destroyProcessor();
		dataOut.destroyProcessor();

		super.closeAll();
	}

	/**
	 * This method will force the string hub to query the driver for a list of
	 * DOMs. For a DOM to be detected its cardX/pairY/domZ/id procfile must
	 * report a valid non-zero DOM mainboard ID.
	 * @throws IOException
	 */
	private List<DOMChannelInfo> discover()
		throws IOException
	{
		if (!isSim) {
			driver.setBlocking(true);
			return driver.discoverActiveDOMs();
		}

		Collection<DeployedDOM> attachedDOMs =
			domRegistry.getDomsOnHub(getNumber());
		List<DOMChannelInfo> activeDOMs =
			new ArrayList<DOMChannelInfo>(attachedDOMs.size());
		for (DeployedDOM dom : attachedDOMs) {
			int card = (dom.getStringMinor()-1) / 8;
			int pair = ((dom.getStringMinor()-1) % 8) / 2;
			char aorb = 'A';
			// not a major change, but the previous code here used
			// % 2 == 1 to check for oddness.  YES, the string's are all
			// positive integers, but that test would fail for negative
			// numbers.
			if (dom.getStringMinor() % 2 != 0) aorb = 'B';
			activeDOMs.add(new DOMChannelInfo(dom.getMainboardId(), card,
											  pair, aorb));
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

		// Lookup the connected DOMs
		List<DOMChannelInfo> activeDOMs;
		try {
			activeDOMs = discover();
		} catch (IOException iox) {
			logger.error("Cannot discover DOMs on hub " + hubId, iox);
			throw new DAQCompException("Cannot discover hub " + hubId, iox);
		} catch (Throwable t) {
			throw new DAQCompException("Unexpected hub " + hubId +
									   " exception", t);
		}

		if (activeDOMs.size() == 0) {
			throw new DAQCompException("No Active DOMs on hub " + hubId);
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Found " + activeDOMs.size() + " active DOMs.");
		}

		ConfigData cfgData;
		try {
			cfgData = new ConfigData(configName, activeDOMs);
		} catch (JAXPUtilException jux) {
			throw new DAQCompException(jux);
		}

		logger.debug("Configuration successfully loaded -" +
					 " Intersection(DISC, CONFIG).size() = " + cfgData.nch);

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

		conn = new DOMConnector(cfgData.nch);

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
		}

		if (!hitSpooling) {
			// Start the hit merger-sorter
			hitsSort = new MultiChannelMergeSort(cfgData.nch, sender);
		} else {
			// send hits to hit spooler which forwards them to the sorter

			// Rotate hit spooling directories : current <==> last
			File hitSpoolCurrent = new File(hitSpoolDir, "currentRun");
			File hitSpoolLast = new File(hitSpoolDir, "lastRun");
			File hitSpoolTemp = new File(hitSpoolDir, "HitSpool" +
										 runNumber + ".tmp");

			// Note that renameTo and mkdir return false on failure
			if (hitSpoolLast.exists()) {
				if (!hitSpoolLast.renameTo(hitSpoolTemp)) {
					logger.debug("hitSpoolLast renameTo failed");
				}
			}
			if (hitSpoolCurrent.exists()) {
				if (!hitSpoolCurrent.renameTo(hitSpoolLast)) {
					logger.debug("hitSpoolCurrent renameTo failed!");
				}
			}
			if (hitSpoolTemp.exists()) {
				if(!hitSpoolTemp.renameTo(hitSpoolCurrent)) {
					logger.debug("hitSpoolTemp renameTo failed!");
				}
			}
			if (!hitSpoolCurrent.exists()) {
				if(!hitSpoolCurrent.mkdir()) {
					logger.debug("hitSpoolCurrent mkdir failed!");
				}
			}

			FilesHitSpool hitSpooler =
				new FilesHitSpool(sender, configurationPath, hitSpoolCurrent,
								  hitSpoolIval, hitSpoolNumFiles);
			hitsSort = new MultiChannelMergeSort(cfgData.nch, hitSpooler);
		}

		// start remaining merger-sorter objects
		moniSort = new MultiChannelMergeSort(cfgData.nch, monitorConsumer);
		scalSort = new MultiChannelMergeSort(cfgData.nch, supernovaConsumer);
		tcalSort = new MultiChannelMergeSort(cfgData.nch, tcalConsumer);

		for (DOMChannelInfo chanInfo : activeDOMs)
		{
			DOMConfiguration config = cfgData.getDOMConfig(chanInfo.mbid);
			if (config == null) continue;

			String cwd = chanInfo.card + "" + chanInfo.pair + chanInfo.dom;

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

			DeployedDOM domInfo = domRegistry.getDom(chanInfo.mbid);

			LiveTCalMoni moni = new LiveTCalMoni(getAlerter(), domInfo);

			// Associate a GPS service to this card, if not already done
			if (!isSim) {
				GPSService inst = GPSService.getInstance();
				inst.startService(chanInfo.card, moni);
			}

			dc.setDomInfo(domInfo);

			dc.setSoftbootBehavior(cfgData.dcSoftboot);
			hitsSort.register(chanInfo.mbid_numerique);
			moniSort.register(chanInfo.mbid_numerique);
			scalSort.register(chanInfo.mbid_numerique);
			tcalSort.register(chanInfo.mbid_numerique);
			dc.setAlerter(getAlerter());
			dc.setLiveMoni(moni);
			conn.add(dc);
			if (logger.isDebugEnabled()) {
				logger.debug("Starting new DataCollector thread on (" + cwd +
							 ").");
			}
		}

		logger.debug("Starting up HKN1 sorting trees...");

		// Still need to get the data collectors to pick up
		// and do something with the config
		try {
			conn.configure();
		} catch (InterruptedException ie) {
			throw new DAQCompException("Interrupted while waiting for DOMs" +
									   " to finish configuring");
		}
	}

	private AbstractDataCollector
		createDataCollector(boolean isSim, DOMChannelInfo chanInfo,
							DOMConfiguration config,
							MultiChannelMergeSort hitsSort,
							MultiChannelMergeSort moniSort,
							MultiChannelMergeSort tcalSort,
							MultiChannelMergeSort scalSort,
							boolean enable_intervals, double snDistance)
		throws IOException, MessageException
	{
		if (!isSim) {
			DataCollector dc =
				new DataCollector(chanInfo.card, chanInfo.pair, chanInfo.dom,
								  config, hitsSort, moniSort, scalSort,
								  tcalSort, enable_intervals);
			addMBean("DataCollectorMonitor-" + chanInfo, dc);
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

	/**
     * Set the run number inside this component.
     *
     * @param runNumber run number
     */
	public void setRunNumber(int runNumber)
	{
		this.runNumber = runNumber;

		logger.info("Set run number");
		if (conn == null) {
			logger.error("DOMConnector has not been initialized!");
		} else {
			for (AbstractDataCollector adc : conn.getCollectors()) {
				adc.setRunNumber(runNumber);
			}
		}
	}

	/**
	 * Controller wants StringHub to start sending data.
	 * Tell DOMs to start up.
	 */
	public void starting(int runNumber)
		throws DAQCompException
	{
		setRunNumber(runNumber);

		logger.info("StringHub is starting the run.");

		sender.reset();

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
			throw new DAQCompException("Couldn't start DOMs", e);
		}
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

		SimpleOutputEngine[] eng = new SimpleOutputEngine[] {
			moniOut, supernovaOut, tcalOut
		};

		for (int i = 0; i < eng.length; i++) {
			OutputChannel chan = eng[i].getChannel();
			if (chan != null) {
				chan.sendLastAndStop();
			}
		}

		GPSService.getInstance().shutdownAll();
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
		// this will set the run number a bit before it actually starts,
		// but hubs have no other way of knowing when the new number has begun
		setRunNumber(runNumber);
	}

	/**
	 * Return this component's svn version id as a String.
	 *
	 * @return svn version id as a String
	 */
	public String getVersionInfo()
	{
		return "$Id: StringHubComponent.java 15256 2014-11-14 14:43:43Z dglo $";
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

	public DAQComponentOutputProcess getTrackEngineWriter()
	{
		return teOut;
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
		long latestFirst = 0L;
		boolean found = true;

		for (AbstractDataCollector adc : conn.getCollectors()) {
			if (!adc.isZombie()) {
				long val = adc.getFirstHitTime();
				if (val < 0L) {
					found = false;
					break;
				} else if (val > latestFirst) {
					latestFirst = val;
				}
			}
		}

		if (!found) {
			return 0L;
		}

		return latestFirst;
	}

	public long getEarliestLastChannelHitTime()
	{
		long earliestLast = Long.MAX_VALUE;
		boolean found = true;

		for (AbstractDataCollector adc : conn.getCollectors()) {
			if (!adc.isZombie()) {
				long val = adc.getLastHitTime();
				if (val < 0L) {
					found = false;
					break;
				} else if (val < earliestLast) {
					earliestLast = val;
				}
			}
		}

		if (!found) {
			return 0L;
		}

		return earliestLast;
	}

	class ConfigData
	{
		int nch;
		int tcalPrescale = 10;
		boolean dcSoftboot;
		boolean enable_intervals;
		double snDistance = Double.NaN;

		private XMLConfig xmlConfig;

		ConfigData(String configName, List<DOMChannelInfo> activeDOMs)
			throws DAQCompException, JAXPUtilException
		{
			// Parse out tags from 'master configuration' file
			Document doc = JAXPUtil.loadXMLDocument(configurationPath,
													configName);

			xmlConfig = new XMLConfig();

			Node intvlNode =
				JAXPUtil.extractNode(doc, "runConfig/intervals/enabled");
			enable_intervals = parseIntervals(intvlNode, true);

			File domConfigsDir = new File(configurationPath, "domconfigs");

			// Lookup <stringHub hubId='x'> node - if any - and process
			// configuration directives.
			final String hnPath = "runConfig/stringHub[@hubId='" + hubId +
				"']";
			Node hubNode = JAXPUtil.extractNode(doc, hnPath);
			if (hubNode == null) {
				// handle older runconfig files which don't specify hubId
				NodeList dcList =
					JAXPUtil.extractNodeList(doc, "runConfig/domConfigList");

				if (dcList.getLength() > 0) {
					readAllDOMConfigs(domConfigsDir, dcList, true);
				} else {
					// handle really ancient runconfig files
					NodeList shList =
						JAXPUtil.extractNodeList(doc, "runConfig/stringhub");
					if (shList.getLength() > 0) {
						readAllDOMConfigs(domConfigsDir, shList, false);
					}
				}
			} else {
				// normal case
				if (!readDOMConfig(domConfigsDir, hubNode, false)) {
					final String path = "runConfig/domConfigList[@hub='" +
						hubId + "']";
					Node dclNode = JAXPUtil.extractNode(doc, path);

					if (dclNode == null ||
						!readDOMConfig(domConfigsDir, dclNode, true))
					{
						throw new DAQCompException("Cannot read DOM config" +
												   " file for hub " + hubId);
					}
				}

				if (JAXPUtil.extractText(hubNode, "trigger/enabled").
					equalsIgnoreCase("true"))
				{
					logger.error("String triggering not implemented");
				}


				intvlNode = JAXPUtil.extractNode(hubNode, "intervals/enabled");
				enable_intervals = parseIntervals(intvlNode, enable_intervals);

				final String fwdProp = "sender/forwardIsolatedHitsToTrigger";
				final String fwdText = JAXPUtil.extractText(hubNode, fwdProp);
				if (fwdText.equalsIgnoreCase("true")) {
					sender.forwardIsolatedHitsToTrigger();
				}

				final String softProp = "dataCollector/softboot";
				final String softText =
					JAXPUtil.extractText(hubNode, softProp);
				if (softText.equalsIgnoreCase("true")) {
					dcSoftboot = true;
				}

				String tcalPStxt =
					JAXPUtil.extractText(hubNode, "tcalPrescale");
				if (tcalPStxt.length() != 0) {
					tcalPrescale = Integer.parseInt(tcalPStxt);
				}

				Node hsNode = JAXPUtil.extractNode(hubNode, "hitspool");
				if (hsNode == null) {
					// if there is no hitspool child of the stringHub tag
					// look for a default node
					hsNode =
						JAXPUtil.extractNode(hubNode, "runConfig/hitspool");
				}

				hitSpooling=false;
				if (hsNode != null) {
					final String enabled =
						JAXPUtil.extractText(hsNode, "enabled");
					if (enabled.equalsIgnoreCase("true")) {
						hitSpooling = true;
					}

					hitSpoolDir = JAXPUtil.extractText(hsNode, "directory");
					if (hitSpoolDir.length() == 0) {
						hitSpoolDir = "/mnt/data/pdaqlocal";
					}

					final String hsIvalText =
						JAXPUtil.extractText(hsNode, "interval");
					if (hsIvalText.length() > 0) {
						final double interval = Double.parseDouble(hsIvalText);
						hitSpoolIval = (long) (1E10 * interval);
					}

					final String hsNFText =
						JAXPUtil.extractText(hsNode, "numFiles");
					if (hsNFText.length() > 0) {
						hitSpoolNumFiles  = Integer.parseInt(hsNFText);
					}
				}
			}

			final String snDistText =
				JAXPUtil.extractText(doc, "runConfig/setSnDistance");
			if (snDistText.length() > 0) {
				snDistance = Double.parseDouble(snDistText);
			}

			// Dropped DOM detection logic - WARN if channel on string AND in
			// config BUT NOT in the list of active DOMs.  Oh, and count the
			// number of channels that are active AND requested in the config
			// while we're looping

			Set<String> activeDomSet = new HashSet<String>();
			for (DOMChannelInfo chanInfo : activeDOMs)
			{
				activeDomSet.add(chanInfo.mbid);
				if (xmlConfig.getDOMConfig(chanInfo.mbid) != null) {
					nch++;
				}
			}

			if (nch == 0) {
				throw new DAQCompException("No Active DOMs on Hub " + hubId +
										   " selected in configuration.");
			}

			for (DeployedDOM deployedDOM :
					 domRegistry.getDomsOnHub(getNumber()))
			{
				String mbid = deployedDOM.getMainboardId();
				if (!activeDomSet.contains(mbid) &&
					xmlConfig.getDOMConfig(mbid) != null)
				{
					logger.warn("DOM " + deployedDOM +
								" requested in configuration for hub " +
								hubId + " but not found.");

					StringHubAlert.sendDOMAlert(getAlerter(),
												"Dropped DOM",
												0, 0, (char) 0,
												deployedDOM.getMainboardId(),
												deployedDOM.getName(),
												deployedDOM.getStringMajor(),
												deployedDOM.getStringMinor());
				}
			}
		}

		DOMConfiguration getDOMConfig(String mbid)
		{
			return xmlConfig.getDOMConfig(mbid);
		}

		/**
		 * Parse the XML node to enable intervals.
		 *
		 * @param node 'interval' node (may be null)
		 * @param prevValue previous value
		 *
		 * @return new value
		 */
		private boolean parseIntervals(Node node, boolean prevValue)
		{
			boolean val;
			if (node == null) {
				val = prevValue;
			} else {
				val = node.getTextContent().equalsIgnoreCase("true");
			}

			return val;
		}


		/**
		 * Read in DOM config info from run configuration file
		 *
		 * @param dir location of DOM configuration directory
		 * @param nodeList list of DOM configuration nodes
		 * @param oldFormat <tt>true</tt> if nodes are in old format
		 *
		 * @throws DAQCompException if a file cannot be read
		 */
		private void readAllDOMConfigs(File dir, NodeList nodeList,
									   boolean oldFormat)
			throws DAQCompException
		{
			for (int i = 0; i < nodeList.getLength(); i++) {
				readDOMConfig(dir, nodeList.item(i), oldFormat);
			}
		}

		/**
		 * Read in DOM config info from run configuration file
		 *
		 * @param dir location of DOM configuration directory
		 * @param nodeList list of DOM configuration nodes
		 * @param oldFormat <tt>true</tt> if nodes are in old format
		 *
		 * @return <tt>true</tt> if the config file was read
		 *
		 * @throws DAQCompException if the file cannot be read
		 */
		private boolean readDOMConfig(File dir, Node node, boolean oldFormat)
			throws DAQCompException
		{
			String tag;
			if (oldFormat) {
				tag = node.getTextContent();
			} else {
				tag = ((Element) node).getAttribute("domConfig");
				if (tag.equals("")) {
					return false;
				}
			}

			// add ".xml" if it's missing
			if (!tag.endsWith(".xml")) {
				tag = tag + ".xml";
			}

			// load DOM config
			File configFile = new File(dir, tag);
			if (logger.isDebugEnabled()) {
				String realism;
				if (isSim)
					realism = "SIMULATION";
				else
					realism = "REAL DOMS";

				logger.debug("Configuring " + realism
							 + " - loading config from "
							 + configFile.getAbsolutePath());
			}
			FileInputStream in;
			try {
				in = new FileInputStream(configFile);
			} catch (FileNotFoundException fnfe) {
				throw new DAQCompException("Cannot open DOM config file " +
										   configFile, fnfe);
			}

			try {
				xmlConfig.parseXMLConfig(in);
			} catch (Exception ex) {
				throw new DAQCompException("Cannot parse DOM config file " +
										   configFile, ex);
			} finally {
				try {
					in.close();
				} catch (IOException ioe) {
					// ignore errors on close
				}
			}

			return true;
		}
	}
}

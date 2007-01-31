/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */
package icecube.daq.stringhub;

import icecube.daq.bindery.SecondaryStreamConsumer;
import icecube.daq.bindery.StreamBinder;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.configuration.XMLConfig;
import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.SimDataCollector;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.io.PayloadDestinationOutputEngine;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.monitoring.MonitoringData;
import icecube.daq.payload.ByteBufferCache;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IPayloadDestinationCollection;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.sender.RequestInputEngine;
import icecube.daq.sender.Sender;
import icecube.daq.util.DOMRegistry;
import icecube.daq.util.DeployedDOM;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

class BindSource
{
	private static final Logger logger = Logger.getLogger(BindSource.class);

	private int nch;
	private String name;
	private SelectableChannel hitSource;
	private SelectableChannel moniSource;
	private SelectableChannel tcalSource;
	private SelectableChannel snSource;

	private StreamBinder hitBind;
	private StreamBinder moniBind;
	private StreamBinder tcalBind;
	private StreamBinder snBind;

	BindSource(int nch, String name, SelectableChannel hitSource,
			   SelectableChannel moniSource, SelectableChannel tcalSource,
			   SelectableChannel snSource)
	{
		this.nch = nch;
		this.name = name;
		this.hitSource = hitSource;
		this.moniSource = moniSource;
		this.tcalSource = tcalSource;
		this.snSource = snSource;
	}

	String getName()
	{
		return name;
	}

	private void startHitThread(Sender sender)
		throws IOException
	{
		hitBind = new StreamBinder(nch, sender);

		hitBind.register(hitSource, "hits" + name);

		hitBind.start();
		logger.debug("Hit binder started.");
	}

	private void startMoniThread(MasterPayloadFactory payloadFactory,
								 IByteBufferCache bufferManager,
								 PayloadDestinationOutputEngine moniPayloadDest,
								 PayloadDestinationOutputEngine tcalPayloadDest,
								 PayloadDestinationOutputEngine snPayloadDest)
		throws IOException
	{
		SecondaryStreamConsumer ssc =
			new SecondaryStreamConsumer(payloadFactory, bufferManager,
										moniPayloadDest, tcalPayloadDest, 
										snPayloadDest);
		moniBind = new StreamBinder(nch, ssc);

		moniBind.register(moniSource, "moni" + name);

		moniBind.start();
		logger.debug("Monitor binder started.");
	}

	private void startSnThread(MasterPayloadFactory payloadFactory,
							   IByteBufferCache bufferManager,
							   PayloadDestinationOutputEngine moniPayloadDest,
							   PayloadDestinationOutputEngine tcalPayloadDest,
							   PayloadDestinationOutputEngine snPayloadDest)
		throws IOException
	{
		SecondaryStreamConsumer ssc =
			new SecondaryStreamConsumer(payloadFactory, bufferManager,
										moniPayloadDest, tcalPayloadDest, 
										snPayloadDest);
		snBind = new StreamBinder(nch, ssc);

		snBind.register(snSource, "sn" + name);

		snBind.start();
		logger.debug("Supernova binder started.");
	}

	private void startTcalThread(MasterPayloadFactory payloadFactory,
								 IByteBufferCache bufferManager,
								 PayloadDestinationOutputEngine moniPayloadDest,
								 PayloadDestinationOutputEngine tcalPayloadDest,
								 PayloadDestinationOutputEngine snPayloadDest)
		throws IOException
	{
		SecondaryStreamConsumer ssc =
			new SecondaryStreamConsumer(payloadFactory, bufferManager,
										moniPayloadDest,  tcalPayloadDest, 
										snPayloadDest);
		tcalBind = new StreamBinder(nch, ssc);

		tcalBind.register(tcalSource, "tcal" + name);

		tcalBind.start();
		logger.debug("TCAL binder started.");
	}

	void startThreads(Sender sender, MasterPayloadFactory payloadFactory,
					  IByteBufferCache bufferManager,
					  PayloadDestinationOutputEngine moniPayloadDest,
					  PayloadDestinationOutputEngine tcalPayloadDest,
					  PayloadDestinationOutputEngine snPayloadDest)
		throws IOException
	{
		startHitThread(sender);
		startMoniThread(payloadFactory, bufferManager, moniPayloadDest,
						tcalPayloadDest, snPayloadDest);
		startTcalThread(payloadFactory, bufferManager, moniPayloadDest,
						tcalPayloadDest, snPayloadDest);
		startSnThread(payloadFactory, bufferManager, moniPayloadDest,
						tcalPayloadDest, snPayloadDest);
	}

	void stopThreads()
	{
		hitBind.shutdown();
		moniBind.shutdown();
		tcalBind.shutdown();
		snBind.shutdown();
	}
}

public class StringHubComponent extends DAQComponent
{

	private static final Logger logger = Logger.getLogger(StringHubComponent.class);
	
	private boolean isSim = false;
	private Driver driver = Driver.getInstance();
	private Sender sender;
	private ByteBufferCache bufferManager;
	private MasterPayloadFactory payloadFactory;
	private DOMRegistry domRegistry;
	private PayloadDestinationOutputEngine   moniPayloadDest, tcalPayloadDest, supernovaPayloadDest; 
	private DOMConnector conn;
	private List<DOMChannelInfo> activeDOMs;
	private List<BindSource> bindSources;
	
	private String configurationPath;
	private String configured = "NO";
	
	
	public StringHubComponent(int hubId) throws Exception
	{
		super(DAQCmdInterface.DAQ_STRING_HUB, hubId);
		
		bufferManager  = new ByteBufferCache(256, 50000000, 50000000, "PyrateBufferManager");
		addCache(bufferManager);

		payloadFactory = new MasterPayloadFactory(bufferManager);
		sender         = new Sender(hubId, payloadFactory);
		isSim          = Boolean.getBoolean("icecube.daq.stringhub.simulation");

		logger.info("starting up StringHub component " + hubId);
		
        final String COMPONENT_NAME = DAQCmdInterface.DAQ_STRING_HUB;
        PayloadDestinationOutputEngine hitOut =
            new PayloadDestinationOutputEngine(COMPONENT_NAME, hubId, "hitOut");
        hitOut.registerBufferManager(bufferManager);
        addEngine(DAQConnector.TYPE_STRING_HIT, hitOut);

        IPayloadDestinationCollection hitColl = hitOut.getPayloadDestinationCollection();
        sender.setHitOutputDestination(hitColl);
        
        RequestInputEngine reqIn = new RequestInputEngine(COMPONENT_NAME, hubId, "reqIn",
                                   sender, bufferManager, payloadFactory);
        addEngine(DAQConnector.TYPE_READOUT_REQUEST, reqIn);

        PayloadDestinationOutputEngine dataOut =
            new PayloadDestinationOutputEngine(COMPONENT_NAME, hubId, "dataOut");
        dataOut.registerBufferManager(bufferManager);
        addEngine(DAQConnector.TYPE_READOUT_DATA, dataOut);

        IPayloadDestinationCollection dataColl = dataOut.getPayloadDestinationCollection();
        sender.setDataOutputDestination(dataColl);

        MonitoringData monData = new MonitoringData();
        monData.setSenderMonitor(sender);
        addMBean("sender", monData);

        // Following are the payload output engines for the secondary streams
        moniPayloadDest = new PayloadDestinationOutputEngine(COMPONENT_NAME, hubId, "moniOut");
        moniPayloadDest.registerBufferManager(bufferManager);
        addEngine(DAQConnector.TYPE_MONI_DATA, moniPayloadDest);
        tcalPayloadDest = new PayloadDestinationOutputEngine(COMPONENT_NAME, hubId, "tcalOut");
        tcalPayloadDest.registerBufferManager(bufferManager);
        addEngine(DAQConnector.TYPE_TCAL_DATA, tcalPayloadDest);
        supernovaPayloadDest = new PayloadDestinationOutputEngine(COMPONENT_NAME, hubId, "supernovaOut");
        supernovaPayloadDest.registerBufferManager(bufferManager);
        addEngine(DAQConnector.TYPE_SN_DATA, supernovaPayloadDest);
    }

	@Override
	public void setGlobalConfigurationDir(String dirName) 
	{
		super.setGlobalConfigurationDir(dirName);
		configurationPath = dirName;
		logger.info("Setting the ueber configuration directory to " + configurationPath);
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
	public void discover() throws IOException, DocumentException
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
			activeDOMs = driver.discoverActiveDOMs();
			logger.info("Found " + activeDOMs.size() + " active DOMs.");
		}
	}
	
	/**
	 * StringHub responds to a configure request from the controller
	 */
	public void configuring(String configName) throws DAQCompException 
	{

		String realism;
		
		if (isSim)
			realism = "SIMULATION";
		else
			realism = "REAL DOMS";

		configured = "YES";
		
		bindSources = new ArrayList<BindSource>();

		try 
		{
			// Lookup the connected DOMs
			discover();
			
			// Parse out tags from 'master configuration' file
			File masterConfigFile = new File(configurationPath, configName + ".xml");
			FileInputStream fis = new FileInputStream(masterConfigFile);
			
			SAXReader r = new SAXReader();
			Document doc = r.read(fis);
			
			String domConfigTag = doc.selectSingleNode("runConfig/domConfigList").getText();
			
			File configFile = new File(configurationPath, domConfigTag + ".xml");
			logger.info("Configuring " + realism 
					+ " - loading config from " 
					+ configFile.getAbsolutePath());			
			XMLConfig xmlConfig = XMLConfig.parseXMLConfig(new FileInputStream(configFile));

			// Find intersection of discovered / configured channels
			int nch = 0;
			for (DOMChannelInfo chanInfo : activeDOMs)
				if (xmlConfig.getDOMConfig(chanInfo.mbid) != null) nch++;
	
			logger.info("Configuration successfully loaded - Intersection(DISC, CONFIG).size() = " + nch);
			conn = new DOMConnector(nch);
				
			for (DOMChannelInfo chanInfo : activeDOMs)
			{
				DOMConfiguration config = xmlConfig.getDOMConfig(chanInfo.mbid);
				if (config == null) continue;

				String cwd = chanInfo.card + "" + chanInfo.pair + chanInfo.dom;

				Pipe hitPipe = Pipe.open();
				Pipe moniPipe = Pipe.open();
				Pipe tcalPipe = Pipe.open();
				Pipe snPipe = Pipe.open();

				BindSource bs =
					new BindSource(nch, cwd, hitPipe.source(),
								   moniPipe.source(), tcalPipe.source(),
								   snPipe.source());
				bindSources.add(bs);

				AbstractDataCollector dc;
				if (isSim)
				{
					dc = new SimDataCollector(chanInfo, hitPipe.sink(), moniPipe.sink(), snPipe.sink(), tcalPipe.sink());
				}
				else
				{
					dc = new DataCollector(
							chanInfo.card, chanInfo.pair, chanInfo.dom, 
							hitPipe.sink(), moniPipe.sink(), snPipe.sink(), tcalPipe.sink()
						);
				}

				/* queue up the config */
				dc.setConfig(config);
				conn.add(dc);
				logger.debug("Starting new DataCollector thread on (" + cwd + ").");
				dc.start();
				logger.debug("DataCollector thread on (" + cwd + ") started.");				
			}

			logger.info("Starting up HKN1 sorting trees...");
			
			// Still need to get the data collectors to pick up and do something with the config
			conn.configure();
			
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
		
	}

	/**
	 * Controller wants stringhub to start sending data.  Tell DOMs to start up.
	 */
	public void starting() throws DAQCompException
	{
		logger.info("Have I been configured? " + configured);
		
		for (BindSource bs : bindSources)
		{
			try {
				bs.startThreads(sender, payloadFactory,
								getByteBufferCache("UnspecificCache"),
								moniPayloadDest, tcalPayloadDest,
								supernovaPayloadDest);
			} catch (IOException ioe) {
				logger.info("Couldn't start threads for binder " +
							bs.getName());
			}
		}

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
			// throw new DAQCompException(e.getMessage());
		}

        // TODO: This is a hack until real data is passing
        //       through the moni/tcal/sn data channels
        try {
            moniPayloadDest.getPayloadDestinationCollection().stopAllPayloadDestinations();
        } catch (IOException ioe) {
            throw new DAQCompException("Couldn't stop monitoring destination", ioe);
        }
        try {
            tcalPayloadDest.getPayloadDestinationCollection().stopAllPayloadDestinations();
        } catch (IOException ioe) {
            throw new DAQCompException("Couldn't stop tcal destination", ioe);
        }
        try {
            supernovaPayloadDest.getPayloadDestinationCollection().stopAllPayloadDestinations();
        } catch (IOException ioe) {
            throw new DAQCompException("Couldn't stop supernova destination", ioe);
        }
        
        logger.info("Returning from stop.");
	}

}

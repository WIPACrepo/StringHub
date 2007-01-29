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
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

public class StringHubComponent extends DAQComponent
{

	private static final Logger logger = Logger.getLogger(StringHubComponent.class);
	
	private boolean isSim = false;
	private Driver driver = Driver.getInstance();
	private StreamBinder bind, moniBind, tcalBind, supernovaBind;
	private Sender sender;
	private ByteBufferCache bufferManager;
	private MasterPayloadFactory payloadFactory;
	private DOMRegistry domRegistry;
	private PayloadDestinationOutputEngine   moniPayloadDest, tcalPayloadDest, supernovaPayloadDest; 
	private ArrayList<AbstractDataCollector> collectors;
	private List<DOMChannelInfo> activeDOMs;
	
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
			bind = new StreamBinder(nch, sender);
			moniBind = new StreamBinder(nch, new SecondaryStreamConsumer(
					this.payloadFactory, 
					this.getByteBufferCache("UnspecificCache"), 
					this.moniPayloadDest, 
					this.tcalPayloadDest, 
					this.supernovaPayloadDest)
			);
			tcalBind = new StreamBinder(nch, new SecondaryStreamConsumer(
					this.payloadFactory, 
					this.getByteBufferCache("UnspecificCache"), 
					this.moniPayloadDest, 
					this.tcalPayloadDest, 
					this.supernovaPayloadDest)
			);
			supernovaBind = new StreamBinder(nch, new SecondaryStreamConsumer(
					this.payloadFactory, 
					this.getByteBufferCache("UnspecificCache"), 
					this.moniPayloadDest, 
					this.tcalPayloadDest, 
					this.supernovaPayloadDest)
			);
			collectors = new ArrayList<AbstractDataCollector>(nch);
				
			for (DOMChannelInfo chanInfo : activeDOMs)
			{
				DOMConfiguration config = xmlConfig.getDOMConfig(chanInfo.mbid);
				if (config == null) continue;
				AbstractDataCollector dc;
				String cwd = chanInfo.card + "" + chanInfo.pair + chanInfo.dom;
				Pipe hitPipe = Pipe.open();
				Pipe moniPipe = Pipe.open();
				Pipe tcalPipe = Pipe.open();
				Pipe snPipe = Pipe.open();
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
				bind.register(hitPipe.source(), "hits-" + cwd);
				moniBind.register(moniPipe.source(), "moni-" + cwd);
				tcalBind.register(tcalPipe.source(), "tcal-" + cwd);
				supernovaBind.register(snPipe.source(), "supernova-" + cwd);

				/* queue up the config */
				dc.setConfig(config);
				collectors.add(dc);
				logger.debug("Starting new DataCollector thread on (" + cwd + ").");
				dc.start();
				logger.debug("DataCollector thread on (" + cwd + ") started.");				
			}

			logger.info("Starting up HKN1 sorting trees...");
			
			bind.start();
			logger.debug("Hit binder started.");
			moniBind.start();
			logger.debug("Monitor binder started.");
			tcalBind.start();
			logger.debug("TCAL binder started.");
			supernovaBind.start();
			logger.debug("Supernova binder started.");
			
			// Still need to get the data collectors to pick up and do something with the config
			for (AbstractDataCollector dc : collectors) 
			{
				dc.signalConfigure();
			}
			
			// Don't return from this method until all DOMs are configured
			for (AbstractDataCollector dc : collectors)
			{
				if (dc.queryDaqRunLevel() != 2) Thread.sleep(25);
			}
			
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
		
		// Reset the binders
		bind.reset();
		moniBind.reset();
		tcalBind.reset();
		supernovaBind.reset();
		
		try
		{
			for (AbstractDataCollector dc : collectors) 
				while (dc.queryDaqRunLevel() != 2) Thread.sleep(100);
			for (AbstractDataCollector dc : collectors) dc.signalStartRun();
		}
		catch (InterruptedException intx)
		{
			intx.printStackTrace();
			throw new DAQCompException(intx.getMessage());
		}
	}
	
	public void stopping()
            throws DAQCompException
	{
		try
		{
			for (AbstractDataCollector dc : collectors) 
			{
				dc.signalStopRun();
			}
			
			for (AbstractDataCollector dc : collectors)
			{
				while (dc.queryDaqRunLevel() != 2) Thread.sleep(100);
			}
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
        
        // HACK!
        logger.info("Sleeping 21 sec to observe stop messages.");
        try
        {
        	Thread.sleep(20000);
        }
        catch (InterruptedException intx)
        {
        	intx.printStackTrace();
        	throw new DAQCompException("Interrupted sleep.");
        }
        logger.info("Returning from stop.");
	}

}

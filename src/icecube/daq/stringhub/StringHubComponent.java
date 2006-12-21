package icecube.daq.stringhub;

import icecube.daq.bindery.StreamBinder;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.configuration.SimConfig;
import icecube.daq.configuration.XMLConfig;
import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.SimDataCollector;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.io.PayloadDestinationOutputEngine;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQCompServer;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.BasicConfigurator;
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
	private StreamBinder bind;
	private Sender sender;
	private ByteBufferCache bufferManager;
	private MasterPayloadFactory payloadFactory;
	private DOMRegistry domRegistry;
	
	private ArrayList<AbstractDataCollector> collectors;
	private List<DOMChannelInfo> activeDOMs;
	
	private String configurationPath;
	private String configured = "NO";
	
	public static void main(String[] args) throws Exception
	{
		
		BasicConfigurator.configure();
		
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

		new DAQCompServer( new StringHubComponent(hubId), args );
		
        //TODO: just for testing. Remove this later
        //Properties properties = new Properties(System.getProperties());
        //properties.setProperty("icecube.daq.stringhub.componentId", "1001");
        //properties.setProperty("icecube.daq.stringhub.simulation", "true");
        //properties.setProperty("icecube.daq.stringhub.simulation.config", "./config/stringHubSimConfig.xml");

        //properties.setProperty("icecube.daq.stringhub.configPath", "./config");

        //System.setProperties(properties);

        //StringHubComponent shComp = new StringHubComponent(Integer.getInteger("icecube.daq.stringhub.componentId"));
        //shComp.setGlobalConfigurationDir("./config");
        //shComp.configuring("hub1001sim");
        
	}
	
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

	}

	@Override
	public void setGlobalConfigurationDir(String dirName) 
	{
		super.setGlobalConfigurationDir(dirName);
		configurationPath = dirName;
		logger.info("Setting the über configuration directory to " + configurationPath);
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

			collectors = new ArrayList<AbstractDataCollector>(nch);
				
			for (DOMChannelInfo chanInfo : activeDOMs)
			{
				DOMConfiguration config = xmlConfig.getDOMConfig(chanInfo.mbid);
				if (config == null) continue;
				AbstractDataCollector dc;
				String cwd = chanInfo.card + "" + chanInfo.pair + chanInfo.dom;
				// This pipe is the pipe which communicates hit data from collector to sorter
				Pipe pipe = Pipe.open();
				if (isSim)
				{
					dc = new SimDataCollector(chanInfo, pipe.sink());
				}
				else
				{
					dc = new DataCollector(chanInfo, pipe.sink());
				}
				bind.register(pipe.source(), cwd);
				dc.setConfig(config);
				collectors.add(dc);
				logger.debug("Starting new DataCollector thread on (" + cwd + ").");
				dc.start();
				logger.debug("DataCollector thread on (" + cwd + ") started.");				
			}

			Thread.sleep(500);
			
			// Still need to get the data collectors to pick up and do something with the config
			for (AbstractDataCollector dc : collectors) 
			{
				dc.signalConfigure();
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
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new DAQCompException(e.getMessage());
		}
		
		bind.start();
		
	}

	/**
	 * Controller wants stringhub to start sending data.  Tell DOMs to start up.
	 */
	public void starting() throws DAQCompException
	{
		logger.info("Have I been configured? " + configured);
		
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
	
	public void stopping() /* throws DAQCompException */
	{
		try
		{
			for (AbstractDataCollector dc : collectors) dc.signalStopRun();
			for (AbstractDataCollector dc : collectors)
			{
				while (dc.queryDaqRunLevel() != 2) Thread.sleep(100);
				dc.signalShutdown();
				dc.join(100);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			// throw new DAQCompException(e.getMessage());
		}
	}

}

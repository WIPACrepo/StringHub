package icecube.daq.configuration;

import icecube.daq.domapp.BadEngineeringFormat;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.EngineeringRecordFormat;
import icecube.daq.domapp.LocalCoincidenceConfiguration;
import icecube.daq.domapp.MuxState;
import icecube.daq.domapp.PulserMode;
import icecube.daq.domapp.TriggerMode;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class XMLConfig extends DefaultHandler 
{
	private ParserState internalState;
	
	private StringBuffer xmlChars;
	private short fadcSamples;
	private final short[] defaultAtwdSamples = { 128, 128, 128, 0 }; 
	private short[] atwdSamples; 
	private final short[] defaultAtwdWidth = { 2, 2, 2, 2 };
	private short[] atwdWidth;
	private int atwdChannel;  
	
	private enum Direction { UP, DOWN };
	private Direction direction;
	private int delayDistance;
	private HashMap<String, DOMConfiguration> definedDOMConfigs;
	private DOMConfiguration currentConfig;
	private final static Logger logger = Logger.getLogger(XMLConfig.class);
	private final static String[] dacNames = { 
		"atwd0TriggerBias", "atwd0RampTop", "atwd0RampRate", "atwdAnalogRef",
		"atwd1TriggerBias", "atwd1RampTop", "atwd1RampRate", "frontEndPedestal",
		"mpeTriggerDiscriminator", "speTriggerDiscriminator", "fastAdcRef", "internalPulser",
		"ledBrightness", "frontEndAmpLowerClamp", "flasherDelay", "muxBias", "flasherRef"
	};
	private final static int[] dacChannels = {
	    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 14
	};
	
	public XMLConfig()
	{
		definedDOMConfigs = new HashMap<String, DOMConfiguration>();
		xmlChars = new StringBuffer();
		
	}
	
	/**
	 * @return a set of DOM mainboard Ids that contains the full set of
	 * DOMs covered by this configuration collection
	 */
	public Set<String> getConfiguredDomIds()
	{
		return definedDOMConfigs.keySet();
	}
	
	public void characters(char[] ch, int start, int length) throws SAXException 
	{
		xmlChars.append(ch, start, length);
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException 
	{
		String text = xmlChars.toString().trim();
		
		if (localName.equals("triggerMode"))
		{
			if (text.equals("forced"))
				currentConfig.setTriggerMode(TriggerMode.FORCED);
			else if (text.equals("spe"))
				currentConfig.setTriggerMode(TriggerMode.SPE);
			else if (text.equals("mpe"))
				currentConfig.setTriggerMode(TriggerMode.MPE);
			else if (text.equals("flasher"))
				currentConfig.setTriggerMode(TriggerMode.FB);
		}
		else if (localName.equals("pmtHighVoltage"))
		{
			short val = Short.parseShort(text);
			currentConfig.setHV(val);
		}
		else if (localName.equals("analogMux"))
		{
			if (text.equals("off"))
				currentConfig.setMux(MuxState.OFF);
			else if (text.equals("clock"))
				currentConfig.setMux(MuxState.OSC_OUTPUT);
			else if (text.equals("clock2x"))
				currentConfig.setMux(MuxState.SQUARE_40MHZ);
			else if (text.equals("onboardLedCurrent"))
				currentConfig.setMux(MuxState.LED_CURRENT);
			else if (text.equals("flasherLedCurrent"))
				currentConfig.setMux(MuxState.FB_CURRENT);
			else if (text.equals("localCoincidenceDown"))
				currentConfig.setMux(MuxState.LOWER_LC);
			else if (text.equals("localCoincidenceUp"))
				currentConfig.setMux(MuxState.UPPER_LC);
			else if (text.equals("commAdc"))
				currentConfig.setMux(MuxState.COMM_ADC_INPUT);
			else if (text.equals("internalPulser"))
				currentConfig.setMux(MuxState.FE_PULSER);
		}
		else if (localName.equals("samples"))
			atwdSamples[atwdChannel] = Short.parseShort(text);
		else if (localName.equals("width"))
			atwdWidth[atwdChannel] = Short.parseShort(text);
		else if (localName.equals("fadcSamples"))
			fadcSamples = Short.parseShort(text);
		else if (localName.equals("engineeringFormat"))
		{
			try
			{
				currentConfig.setEngineeringFormat(new EngineeringRecordFormat(fadcSamples, atwdSamples, atwdWidth));
			}
			catch (BadEngineeringFormat bex)
			{
				logger.error("Bad engineering format.");
				throw new IllegalStateException(bex);
			}
		}
		else if (localName.equals("localCoincidence"))
		{
			internalState = ParserState.DOM_CONFIG;
		}
		else if (localName.equals("pedestalSubtract"))
		{
			if (text.equals("true"))
				currentConfig.setPedestalSubtraction(true);
			else
				currentConfig.setPedestalSubtraction(false);
		}				
		else if (localName.equals("pulserMode"))
		{
			if (text.equals("beacon"))
				currentConfig.setPulserMode(PulserMode.BEACON);
			else if (text.equals("pulser"))
				currentConfig.setPulserMode(PulserMode.PULSER);
		}
		else if (localName.equals("pulserRate"))
		{
			currentConfig.setPulserRate(Integer.parseInt(text));
		}
		else if (internalState == ParserState.LOCAL_COINCIDENCE)
		{
			if (localName.equals("type"))
			{
				if (text.equals("soft"))
					currentConfig.getLC().setType(LocalCoincidenceConfiguration.Type.SOFT);
				else if (text.equals("hard"))
					currentConfig.getLC().setType(LocalCoincidenceConfiguration.Type.HARD);
			}
			else if (localName.equals("mode"))
			{
				if (text.equals("none"))
					currentConfig.getLC().setRxMode(LocalCoincidenceConfiguration.RxMode.RXNONE);
				else if (text.equals("up-or-down"))
					currentConfig.getLC().setRxMode(LocalCoincidenceConfiguration.RxMode.RXEITHER);
				else if (text.equals("up"))
					currentConfig.getLC().setRxMode(LocalCoincidenceConfiguration.RxMode.RXUP);
				else if (text.equals("down"))
					currentConfig.getLC().setRxMode(LocalCoincidenceConfiguration.RxMode.RXDOWN);
				else if (text.equals("up-and-down"))
				    currentConfig.getLC().setRxMode(LocalCoincidenceConfiguration.RxMode.RXBOTH);
				else if (text.equals("headers-only"))
				    currentConfig.getLC().setRxMode(LocalCoincidenceConfiguration.RxMode.RXHDRS);
			}
			else if (localName.equals("txMode"))
			{
				if (text.equals("none"))
					currentConfig.getLC().setTxMode(LocalCoincidenceConfiguration.TxMode.TXNONE);
				else if (text.equals("both"))
					currentConfig.getLC().setTxMode(LocalCoincidenceConfiguration.TxMode.TXBOTH);
				else if (text.equals("up"))
					currentConfig.getLC().setTxMode(LocalCoincidenceConfiguration.TxMode.TXUP);
				else if (text.equals("down"))
					currentConfig.getLC().setTxMode(LocalCoincidenceConfiguration.TxMode.TXDOWN);
			}
			else if (localName.equals("span"))
			{
				currentConfig.getLC().setSpan(Byte.parseByte(text));
			}
			else if (localName.equals("preTrigger"))
			{
				currentConfig.getLC().setPreTrigger(Integer.parseInt(text));
			}
			else if (localName.equals("postTrigger"))
			{
				currentConfig.getLC().setPostTrigger(Integer.parseInt(text));
			}
			else if (localName.equals("cableLength"))
			{
				if (direction == Direction.DOWN)
					currentConfig.getLC().setCableLengthDn(delayDistance - 1, Short.parseShort(text));
				else
					currentConfig.getLC().setCableLengthUp(delayDistance - 1, Short.parseShort(text));
			}
        }
		else if (localName.equals("deadtime"))
		{
		    currentConfig.setSupernovaDeadtime(Integer.parseInt(text));
		}
		else if (localName.equals("disc"))
		{
		    boolean spe;
		    if (text.equals("spe"))
		        spe = true;
		    else
		        spe = false;
			currentConfig.setSupernovaSpe(spe);
		}
        else if (localName.equals("hardwareMonitorInterval"))
        {
            currentConfig.setHardwareMonitorInterval((int) (40000000 * Double.parseDouble(text))); 
        }
        else if (localName.equals("fastMonitorInterval"))
        {
            currentConfig.setFastMonitorInterval((int) (40000000 * Double.parseDouble(text)));
        }
		else if (localName.equals("noiseRate"))
		{
			currentConfig.setSimNoiseRate(Double.parseDouble(text));
		}
		else if (internalState == ParserState.DOM_CONFIG)
		{
			// Name not found - try the DACs
			for (int idac = 0; idac < dacNames.length; idac++)
			{
				if (localName.equals(dacNames[idac]))
				{
					short val = Short.parseShort(text);
					int ch = dacChannels[idac];
					currentConfig.setDAC(ch, val);
					break;
				}
			}
		}
		
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException 
	{
		xmlChars.setLength(0);
		if (localName.equals("atwd"))
		{
			atwdChannel = Integer.parseInt(attributes.getValue("ch"));
		}
		else if (localName.equals("deltaCompressed"))
		{
			currentConfig.enableDeltaCompression();
		}
		else if (localName.equals("supernovaMode"))
		{
            int index = attributes.getIndex("enabled");
            if (index >= 0 && attributes.getValue(index).equals("true"))
				currentConfig.enableSupernova();
			else
				currentConfig.disableSupernova();
				
		}
		else if (localName.equals("engineeringFormat"))
		{
			fadcSamples = 250;
			atwdSamples = new short[4];
			System.arraycopy(defaultAtwdSamples, 0, atwdSamples, 0, 4);
			atwdWidth = new short[4];
			System.arraycopy(defaultAtwdWidth, 0, atwdWidth, 0, 4);
		}
		else if (localName.equals("localCoincidence"))
		{
			internalState = ParserState.LOCAL_COINCIDENCE;
		}
		else if (localName.equals("cableLength"))
		{
			if (attributes.getValue("dir").equals("up"))
				direction = Direction.UP;
			else
				direction = Direction.DOWN;
			delayDistance = Integer.parseInt(attributes.getValue("dist"));
		}
		else if (localName.equals("domConfig"))
		{
			currentConfig = new DOMConfiguration();
			String mbid = attributes.getValue("mbid");
			definedDOMConfigs.put(mbid, currentConfig);
			internalState = ParserState.DOM_CONFIG;
		}
	}

	public void parseXMLConfig(InputStream xmlIn) throws Exception
	{
		final String schemaPath = "domconfig.xsd";
		SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		// ClassLoader cl = Thread.currentThread().getContextClassLoader();
		// InputStream schemaStream = XMLConfig.class.getResourceAsStream(schemaPath);
		// if (schemaStream == null) throw new FileNotFoundException(schemaPath);
		// Schema schema = schemaFactory.newSchema(new StreamSource(schemaStream));
		// saxFactory.setSchema(schema);
		saxFactory.setNamespaceAware(true);
		SAXParser parser = saxFactory.newSAXParser();
		try
		{
		    long t0 = System.currentTimeMillis();
			parser.parse(xmlIn, this);
			logger.info("XML parsing completed - took " + 
						(System.currentTimeMillis() - t0) +
						" milliseconds.");
		}
		catch (Exception except)
		{
			except.printStackTrace();
			throw except;
		}
	}
	
	public DOMConfiguration getDOMConfig(String mbid)
	{
		return definedDOMConfigs.get(mbid);
	}

}

enum ParserState
{
	INIT, DOM_CONFIG, LOCAL_COINCIDENCE
};

package icecube.daq.xmlconfig.test;

import static org.junit.Assert.assertNotNull;
import icecube.daq.configuration.XMLConfig;
import icecube.daq.domapp.DOMConfiguration;

import java.io.InputStream;

import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.Test;
import org.junit.BeforeClass;

public class XMLConfigTest 
{
	private XMLConfig xmlConfig;
	private DOMConfiguration config;
	
	@BeforeClass public static void initialize() throws Exception
	{
		BasicConfigurator.configure();
	}
	
	@Before public void setUp() throws Exception
	{
		InputStream xmlIn = ClassLoader.getSystemResourceAsStream("ic3/daq/configuration/sample-config.xml");
		xmlConfig = XMLConfig.parseXMLConfig(xmlIn);
		config = xmlConfig.getDOMConfig("57bc3f3a220d");
	}

	@Test public void testGotDOMConfigs() 
	{
		// look for the dom configs declared
		assertNotNull(config);
	}
	
	@Test public void testFormat()
	{
	}
	
	@Test public void testTrigger()
	{
	}
	
	@Test public void testLocalCoincidence()
	{
	}
	
	@Test public void testSupernova()
	{
	}
}

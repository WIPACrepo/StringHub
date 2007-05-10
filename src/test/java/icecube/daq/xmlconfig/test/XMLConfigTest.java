package icecube.daq.xmlconfig.test;

import static org.junit.Assert.assertNotNull;
import icecube.daq.configuration.XMLConfig;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.test.TestDeltaMCodec;

import java.io.InputStream;

import junit.framework.JUnit4TestAdapter;

import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.Test;
import org.junit.BeforeClass;

public class XMLConfigTest 
{
	private XMLConfig xmlConfig;
	private DOMConfiguration config;
	
	/**
	 * This should make the tests JUnit 3.8 compatible
	 * @return
	 */
	public static junit.framework.Test suite()
	{
		return new JUnit4TestAdapter(XMLConfigTest.class);
	}
		
	@BeforeClass public static void initialize() throws Exception
	{
		BasicConfigurator.configure();
	}
	
	@Before public void setUp() throws Exception
	{
		InputStream xmlIn = getClass().getClassLoader().getResourceAsStream("icecube/daq/configuration/sample-config.xml");
		xmlConfig = new XMLConfig();
		xmlConfig.parseXMLConfig(xmlIn);
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

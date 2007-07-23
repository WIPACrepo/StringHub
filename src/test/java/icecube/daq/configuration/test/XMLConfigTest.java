package icecube.daq.configuration.test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import icecube.daq.configuration.XMLConfig;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.TriggerMode;
import icecube.daq.domapp.test.TestDeltaMCodec;
import icecube.daq.stringhub.test.MockAppender;

import java.io.InputStream;

import junit.framework.JUnit4TestAdapter;

import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.Test;
import org.junit.BeforeClass;

public class XMLConfigTest 
{
	private static final MockAppender appender = new MockAppender();

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
		BasicConfigurator.resetConfiguration();
		BasicConfigurator.configure(appender);
	}
	
	@Before public void setUp() throws Exception
	{
        InputStream xmlIn = XMLConfig.class.getResourceAsStream("sample-config.xml");
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
        assertFalse(config.isDeltaCompressionEnabled());
        assertEquals((short) 128, config.getEngineeringFormat().atwdSamples(0));
        assertEquals((short)  32, config.getEngineeringFormat().atwdSamples(1));
        assertEquals((short)  32, config.getEngineeringFormat().atwdSamples(2));
        assertEquals((short)   0, config.getEngineeringFormat().atwdSamples(3));
        assertEquals((short) 200, config.getEngineeringFormat().fadcSamples());
	}
	
	@Test public void testTrigger()
	{
        assertEquals(TriggerMode.SPE, config.getTriggerMode());
        assertEquals((short) 665, config.getDAC(8));
        assertEquals((short) 569, config.getDAC(9));
	}
	
	@Test public void testLocalCoincidence()
	{
        assertEquals(1250, config.getLC().getPreTrigger());
        assertEquals(1450, config.getLC().getPostTrigger());
        assertEquals((byte) 3, config.getLC().getSpan());
	}
	
	@Test public void testMonitorIntervals()
	{
        assertEquals(168000000, config.getHardwareMonitorInterval());
	}
}

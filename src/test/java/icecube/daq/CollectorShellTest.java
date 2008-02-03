/**
 * 
 */
package icecube.daq;

import static org.junit.Assert.*;
import icecube.daq.util.FlasherboardConfiguration;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

/**
 * @author kael
 *
 */
public class CollectorShellTest
{

    CollectorShell shell;
    
    public CollectorShellTest()
    {
        BasicConfigurator.configure();
    }
    
    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {
        shell = new CollectorShell();
    }

    /**
     * Test method for {@link icecube.daq.CollectorShell#parseOption(java.lang.String)}.
     */
    @Test
    public void testParseOptionFlasher() throws Exception
    {
        shell.parseOption("flasher");
        assertNotNull(shell.getFlasherConfig());
        shell.parseOption("flasher:width=14,delay=150,brightness=48,rate=100,mask=55ff");
        FlasherboardConfiguration fc = shell.getFlasherConfig();
        assertEquals(48, fc.getBrightness());
        assertEquals(14, fc.getWidth());
        assertEquals(150, fc.getDelay());
        assertEquals(0x55ff, fc.getMask());
        assertEquals(100, fc.getRate());
    }
    
    @Test
    public void testParseOptionDAC() throws Exception
    {
        shell.parseOption("dac03=1588");
        shell.parseOption("dac14=395");
        assertEquals((short) 1588, shell.getConfig().getDAC(3));
        assertEquals((short) 395,  shell.getConfig().getDAC(14));
    }
    
    @Test
    public void testParseOptionDebug() throws Exception
    {
        Logger logger = Logger.getLogger("icecube.daq.rapcal.AbstractRAPCal");
        shell.parseOption("debug:icecube.daq.rapcal.AbstractRAPCal");
        assertTrue(logger.getLevel().equals(Level.DEBUG));
    }

}

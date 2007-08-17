/**
 * 
 */
package icecube.daq;

import static org.junit.Assert.*;
import icecube.daq.util.FlasherboardConfiguration;

import org.junit.Before;
import org.junit.Test;

/**
 * @author kael
 *
 */
public class CollectorShellTest
{

    CollectorShell shell;
    
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

}

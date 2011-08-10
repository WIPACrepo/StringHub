package icecube.daq.domapp;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

/**
 * This will test a real DOM so you had better have a real DOM attached.
 * @author kael
 *
 */
public class RealDOMAppTest
{
    private DOMApp  dom;
    private static Logger logger = Logger.getLogger(RealDOMAppTest.class);
    
    private short[] dacs = new short[] { 
            850, 2300, 350, 2250, 
            850, 2300, 350, 2130, 
            650,  575, 800,    0, 
            1023, 800, 450, 1023 
            };
    
    public RealDOMAppTest()
    {
        BasicConfigurator.configure();
    }
    
    private ArrayList<DeltaCompressedHit> decodeHits(ByteBuffer buf)
    {
        ArrayList<DeltaCompressedHit> hitList = new ArrayList<DeltaCompressedHit>();
        int len = buf.getShort();
        int fmt = buf.getShort();
        int clkMSB = buf.getShort();
        buf.order(ByteOrder.LITTLE_ENDIAN);
        while (buf.remaining() > 0) hitList.add(DeltaCompressedHit.decodeBuffer(buf, clkMSB));
        return hitList;
    }
    
    @Before
    public void setUp() throws Exception
    {
        try {
            dom = new DOMApp(0, 0, 'A');
        } catch (IOException iox) {
            logger.warn("Unable to open DOM device file.  This test requires real DOM hardware.");
            return;
        }
        
        for (byte idac = 0; idac < 16; idac++) dom.writeDAC(idac, dacs[idac]);
        dom.setDeltaCompressionFormat();
        dom.disableHV();
        dom.disableMinBias();
        dom.disableSupernova();
        dom.setPulserRate((short) 20);
    }

    @Test
    public void testCollectPedestals() throws Exception
    {
        if (dom == null) return;
        dom.beginRun();
        Thread.sleep(2500);
        ByteBuffer buf = dom.getData();
        dom.endRun();
        for (DeltaCompressedHit hit : decodeHits(buf)) {
            short atwd[][] = hit.getATWD();
            for (short[] w : atwd) 
                for (short x : w)
                    assertTrue(x > 197 && x < 203); 
        }
    }

}

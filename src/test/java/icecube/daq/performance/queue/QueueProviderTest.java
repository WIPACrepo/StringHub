package icecube.daq.performance.queue;

import icecube.daq.performance.common.PowersOfTwo;
import org.junit.Test;


import static org.junit.Assert.*;

/**
 * Tests QueueProvider.java
 */
public class QueueProviderTest
{

    @Test
    public void testSubsystems()
    {
        for(QueueProvider.Subsystem subsystem :
                QueueProvider.Subsystem.values())
        {
            subsystem.createQueue(PowersOfTwo._1024);
        }
    }

    @Test
    public void testMPSCOptions()
    {
        for(QueueProvider.MPSCOption option :
                QueueProvider.MPSCOption.values() )
        {
            option.createQueue(PowersOfTwo._1024);

            String allcaps = option.name().toUpperCase();

            QueueProvider.MPSCOption lookup =
                    QueueProvider.MPSCOption.valueOf(allcaps);

            assertSame(option, lookup);
        }
    }

    @Test
    public void testSPSCOptions()
    {
        for(QueueProvider.SPSCOption option :
                QueueProvider.SPSCOption.values() )
        {
            option.createQueue(PowersOfTwo._1024);

            String allcaps = option.name().toUpperCase();

            QueueProvider.SPSCOption lookup =
                    QueueProvider.SPSCOption.valueOf(allcaps);

            assertSame(option, lookup);
        }
    }

}

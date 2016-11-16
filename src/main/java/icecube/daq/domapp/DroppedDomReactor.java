package icecube.daq.domapp;

import org.apache.log4j.Logger;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * Receives dropped dom callbacks and initiates diagnostic actions to
 * illuminate any systematic issues.
 */
public class DroppedDomReactor
{

    private final Logger logger = Logger.getLogger(DroppedDomReactor.class);

    /** Count of reported dropped DOMs. */
    private int numDropped;

    /** Sentinel for detail logging. */
    private boolean loggedGCDetails;

    /** Number of dropped DOMS that trigger a GC detail logging. */
    private final static int LOG_GC_DETAIL_THRESHOLD = 10;

    /** Singleton instance. */
    public static final DroppedDomReactor singleton = new DroppedDomReactor();

    /**
     * Callback from collectors to report dropped DOM
     * @param collector Identifies the source.
     */
    public void reportDroppedDom(final DataCollector collector)
    {
        synchronized (this)
        {
            numDropped++;

            // log gc details after a certain number of drops are reported
            if( !loggedGCDetails && numDropped >= LOG_GC_DETAIL_THRESHOLD)
            {
                logGCDetail();
                loggedGCDetails = true;
            }
        }
    }


    private void logGCDetail()
    {
        try
        {
            // Note: This is hard-coded to an oracle/openjdk generational
            //       collector
            StringBuilder sb = new StringBuilder();
            for (GarbageCollectorMXBean garbageCollectorMXBean :
                    ManagementFactory.getGarbageCollectorMXBeans())
            {
                long collectionTime =
                        garbageCollectorMXBean.getCollectionTime();
                long count = garbageCollectorMXBean.getCollectionCount();
                String name = garbageCollectorMXBean.getName();
                sb.append("[collector: ").append(name)
                        .append(", collections: ").append(count)
                        .append(", time: ").append(collectionTime/1000)
                        .append("]");
            }

            logger.warn("GC Stats: " + sb.toString());

        }
        catch (Throwable th)
        {
            logger.error("Can't log GC details:", th);
        }
    }


}

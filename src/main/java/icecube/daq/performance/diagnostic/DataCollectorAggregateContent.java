package icecube.daq.performance.diagnostic;

import icecube.daq.domapp.DataCollectorMBean;
import icecube.daq.domapp.RunLevel;

import java.util.Collection;

/**
 * Provides trace content that is an aggregation of counters from
 * a collection of DataCollectors.
 *
 * <PRE>
 *   Example:
 *
 *   chan  actv lbm   hit/sec    lc/sec     procq      dispatchq
 *   64    64   42    55149      9241       22432      39068
 *   64    64   43    55332      9409       22981      40182
 *   64    64   43    55332      9409       23590      40715
 *   64    64   44    54254      8965       23375      36506
 *   64    64   44    53730      8721       23644      38477
 *   64    64   44    53732      8719       23910      37804
 *
 * </PRE>
 */
public class DataCollectorAggregateContent implements Content
{
//    private final DataCollector[] collectors;
    private final DataCollectorMBean[] collectors;
    private final String header;

    public DataCollectorAggregateContent(
            final Collection<? extends DataCollectorMBean> collectors)
    {
       this(collectors.toArray(new DataCollectorMBean[collectors.size()]));
    }

    public DataCollectorAggregateContent(final DataCollectorMBean[] collectors)
    {
        this.collectors = collectors;
        this.header = String.format("%-5s %-5s %-5s %-10s %-10s %-10s %-10s",
                "chan",
                "actv",
                "lbm",
                "hit/sec",
                "lc/sec",
                "procq",
                "dispatchq");
    }

    @Override
    public void header(final StringBuilder sb)
    {
        sb.append(header);
    }

    @Override
    public void content(final StringBuilder sb)
    {
        // aggregate
        int active=0;
        int processorQueueCount = 0;
        int dispatchQueueCount = 0;
        double hitsPerSecondIn = 0.0;
        double lcHitsPerSecondIn = 0.0;
        int lmbOverflows = 0;
        for (int i = 0; i < collectors.length; i++)
        {
            // ## collector ##
            DataCollectorMBean collector = collectors[i];
            boolean alive =
                    collector.getRunState().equals(RunLevel.RUNNING.name());
            if(alive)
            {
                active++;
            }
            // ## processors ##
            processorQueueCount += collector.getHitProcessorQueueDepth()[0];

            // ## dispatch ##
            dispatchQueueCount += collector.getHitDispatcherQueueDepth()[0];

            // ## acquisition input ##
            hitsPerSecondIn += collector.getHitRate();
            lcHitsPerSecondIn += collector.getHitRateLC();
            lmbOverflows += collector.getLBMOverflowCount();
        }

        sb.append(String.format("%-5d %-5d %-5d %-10.0f %-10.0f %-10d %-10d",
                collectors.length,
                active,
                lmbOverflows,
                hitsPerSecondIn,
                lcHitsPerSecondIn,
                processorQueueCount,
                dispatchQueueCount));
    }

}

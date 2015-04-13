package icecube.daq.domapp;

import icecube.daq.bindery.BufferConsumer;

/**
 * Isolate DataCollector implementation knowledge, especially
 * while we transition to the asynchronous design.
 */
public class DataCollectorFactory
{

    /** During transition, allow fallback to the original implementation*/
    private static final boolean USE_ORIGINAL_IMPLEMENTATION =
            Boolean.getBoolean("icecube.daq.domapp.datacollector.use-original-impl");

    public static AbstractDataCollector buildDataCollector(int card,
                                                           int pair,
                                                           char dom,
                                                           String mbid,
                                                           DOMConfiguration config,
                                                           BufferConsumer hitsTo,
                                                           BufferConsumer moniTo,
                                                           BufferConsumer supernovaTo,
                                                           BufferConsumer tcalTo,
                                                           boolean enable_intervals)
    {
        if(USE_ORIGINAL_IMPLEMENTATION)
        {
            return new DataCollector(card, pair, dom, mbid, config,
                    hitsTo, moniTo, supernovaTo, tcalTo, enable_intervals);
        }
        else
        {
            return new NewDataCollector(card, pair, dom, mbid, config,
                    hitsTo, moniTo, supernovaTo, tcalTo, enable_intervals);
        }
    }
}

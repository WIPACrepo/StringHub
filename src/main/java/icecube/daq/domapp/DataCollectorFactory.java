package icecube.daq.domapp;

import icecube.daq.bindery.BufferConsumer;

/**
 * Isolate DataCollector implementation knowledge,
 */
public class DataCollectorFactory
{

    public static DataCollector buildDataCollector(int card,
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
        return new DataCollector(card, pair, dom, mbid, config,
                hitsTo, moniTo, supernovaTo, tcalTo, enable_intervals);
    }

}

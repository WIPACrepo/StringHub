/* -*- mode: java; indent-tabs-mode:f; tab-width:4 -*- */

package icecube.daq.domapp;


/**
 * Defines the DOM based data collector implementations, for use by
 * StringHubComponent and Omicron.
 *
 * This class hides the fact that we have more than one DataCollector
 * implementation available in the code base.
 *
 * @see LegacyDataCollector
 * @see NewDataCollector
 */
public abstract class DataCollector
    extends AbstractDataCollector
    implements DataCollectorMBean
{
    protected DataCollector(final int card, final int pair, final char dom)
    {
        super(card, pair, dom);
    }
}

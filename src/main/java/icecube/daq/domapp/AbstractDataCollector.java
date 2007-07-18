/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */

package icecube.daq.domapp;

import icecube.daq.util.FlasherboardConfiguration;

import org.apache.log4j.Logger;

/**
 * Abstract base class for DataCollectors (real / sim / other).
 * This class includes the basic state drivers but lacks anything
 * that does any data collection.
 * A real DOM data collector does the following things:
 * <ul>
 * <li>Configures the DOM given a configuration class,</li>
 * <li>Collects data from the <b>hit</b>, <b>monitor</b>,
 * <b>supernova</b>, and <b>tcal</b> sources and (optionally)
 * sends the data out on channels supplied by the 'caller.'</li>
 * </ul> 
 * @author krokodil
 *
 */
public abstract class AbstractDataCollector extends Thread
{
    protected int card;
    protected int pair;
    protected char dom;
    
    protected String mbid;
    protected RunLevel runLevel;
    protected DOMConfiguration config;
    protected FlasherboardConfiguration flasherConfig;
    private static final Logger logger = Logger.getLogger(AbstractDataCollector.class);
    
    public AbstractDataCollector(int card, int pair, char dom)
    {
        this.card = card;
        this.pair = pair;
        this.dom  = dom;
        mbid      = null;
        runLevel  = RunLevel.INITIALIZING;
        config    = null;
        flasherConfig = null;
    }
    
    public int getCard() { return card; }
    public int getPair() { return pair; }
    public char getDom() { return dom; }
	public String getMainboardId() { return mbid; }
	
	public void setConfig(DOMConfiguration config)
	{
	    this.config = config;
	}
	
	public DOMConfiguration getConfig()
	{
	    return config;
	}
	
	public void setFlasherConfig(FlasherboardConfiguration fbc)
	{
	    this.flasherConfig = fbc;
	}
	
	public FlasherboardConfiguration getFlasherConfig()
	{
	    return flasherConfig;
	}
	
	/**
	 * Tells the collector to begin a configure operation.  This will not happen
	 * immediately but is deferred until the collector thread's next pass through
	 * the activity loop.  When the configure is complete the state will be
	 * CONFIGURED. 
	 */
	public void signalConfigure()
	{
	    switch (getRunLevel())
	    {
	    case ZOMBIE: return;
	    case IDLE:
	    case CONFIGURED:
	        setRunLevel(RunLevel.CONFIGURING);
	        break;
	    default:
	        logger.error("Attempted to configure DOM at run level " + runLevel);
	    }
	}
	
	/**
	 * Asynchronously start a run.  The operation does not begin until the
	 * collector thread's next pass through the activity loop.  
	 */
	public void signalStartRun()
	{
	    switch (getRunLevel())
	    {
	    case CONFIGURED:
	        setRunLevel(RunLevel.STARTING);
	        break;
        default:
            logger.error("Attempted to start run on DOM at run level " + runLevel);
	    }
	}
	
	public void signalStopRun()
	{
	    switch (getRunLevel())
	    {
	    case RUNNING:
	        setRunLevel(RunLevel.STOPPING);
	        break;
        default:
            logger.info("Ignoring redundant stop from run level " + runLevel);
	    }
	}
	
	public abstract void signalShutdown();
	
	public synchronized RunLevel getRunLevel()
	{
	    return runLevel;
	}
	
	public synchronized void setRunLevel(RunLevel runLevel)
	{
	    this.runLevel = runLevel;
	}
	
	
	public abstract void close();

	// Monitoring facility
	public abstract long getNumHits();
	public abstract long getNumMoni();
	public abstract long getNumTcal();
	public abstract long getNumSupernova();
	public abstract long getAcquisitionLoopCount();
}

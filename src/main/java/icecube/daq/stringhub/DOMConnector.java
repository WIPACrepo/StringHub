/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */

package icecube.daq.stringhub;

import org.apache.log4j.Logger;
import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQConnector;

import java.util.ArrayList;

/***
 * StringHub connector for data collectors.
 */
public class DOMConnector
	extends DAQConnector
{
	/** DOM data collectors. */
	private ArrayList<AbstractDataCollector> collectors;
	private static final Logger logger = Logger.getLogger(DOMConnector.class);

	/**
	 * Create a DAQ input connector.
	 *
	 * @param nch number of channels
	 */
	public DOMConnector(int nch)
	{
		super("DOMs");

		collectors = new ArrayList<AbstractDataCollector>();
	}

	/**
	 * Add a data collector.
	 *
	 * @param dc data collector
	 */
	public void add(AbstractDataCollector dc)
	{
		collectors.add(dc);
	}

	/**
	 * Configure data collectors.
	 */
	public void configure()
	{
		for (AbstractDataCollector dc : collectors) {
			dc.signalConfigure();
		}

		int configured_counter = 0;
		// wait for things to configure
		for (AbstractDataCollector dc : collectors) {
			while (dc.queryDaqRunLevel() !=
				   AbstractDataCollector.CONFIGURED)
			{
				try {
					Thread.sleep(100);
				} catch (InterruptedException ie) {
					// ignore interrupts
				}
			}
			configured_counter++;
			logger.info("Configured DOM count = " + configured_counter);
		}
		logger.info("All DOMs configured.");
	}

	/**
	 * Destroy this connector.
	 *
	 * @throws Exception if there was a problem
	 */
	public void destroy()
		throws Exception
	{
		for (AbstractDataCollector dc : collectors) {
			dc.signalShutdown();
			dc.close();
		}

		int iLoop = 0;
		for (AbstractDataCollector dc : collectors) {
			while (iLoop++ < 250) {
				if (dc.queryDaqRunLevel() == AbstractDataCollector.CONFIGURED) 
					break;
				Thread.sleep(50);
			}
			if (iLoop == 250) throw new Error("timeout on shutdown of " + dc.getName());
		}
	}

	/**
	 * Force engine to stop processing data.
	 *
	 * @throws Exception if there is a problem
	 */
	public void forcedStopProcessing()
		throws Exception
	{
		throw new Error("Unimplemented");
	}

	/**
	 * Get current engine state.
	 *
	 * @return state string
	 */
	public String getState()
	{
		throw new Error("Unimplemented");
	}

	/**
	 * Is this connector running?
	 *
	 * @return <tt>true</tt> if this connector is running
	 */
	public boolean isRunning()
	{
		for (AbstractDataCollector dc : collectors) {
			if (dc.queryDaqRunLevel() != AbstractDataCollector.RUNNING) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Is this connector stopped?
	 *
	 * @return <tt>true</tt> if this connector is stopped
	 */
	public boolean isStopped()
	{
		for (AbstractDataCollector dc : collectors) {
			if (dc.queryDaqRunLevel() != AbstractDataCollector.CONFIGURED) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Start background threads.
	 *
	 * @throws Exception if there is a problem
	 */
	public void start()
		throws Exception
	{
		// do nothing
	}

	/**
	 * Start processing data.
	 *
	 * @throws Exception if there is a problem
	 */
	public void startProcessing()
		throws Exception
	{
		for (AbstractDataCollector dc : collectors) {
			dc.signalStartRun();
		}
	}

	/**
	 * Stop DOM data collectors.
	 *
	 * @throws Exception if there is a problem
	 */
	public void stopProcessing()
		throws Exception
	{
		for (AbstractDataCollector dc : collectors) {
			dc.signalStopRun();
		}

		for (AbstractDataCollector dc : collectors) {
			while (dc.queryDaqRunLevel() !=
				   AbstractDataCollector.CONFIGURED)
			{
				try {
					Thread.sleep(25);
				} catch (InterruptedException ie) {
					// ignore interrupts
				}
			}
		}
	}

	public ArrayList<AbstractDataCollector> getCollectors() {
		return collectors;
	}

	/**
	 * String representation.
	 *
	 * @return debugging string
	 */
	public String toString()
	{
		return "DOMConn[]";
	}
}

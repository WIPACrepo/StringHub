/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */

package icecube.daq.stringhub;

/*
import icecube.daq.io.DAQComponentInputProcessor;
import icecube.daq.io.PayloadInputEngine;

import icecube.daq.payload.IByteBufferCache;

import java.io.IOException;
*/

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

		// wait for things to configure
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

	/**
	 * Destroy this connector.
	 *
	 * @throws Exception if there was a problem
	 */
	public void destroy()
		throws Exception
	{
		throw new Error("Unimplemented");
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

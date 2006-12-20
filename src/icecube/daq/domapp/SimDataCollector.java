package icecube.daq.domapp;

import icecube.daq.bindery.StreamBinder;
import icecube.daq.dor.DOMChannelInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.log4j.Logger;

import cern.jet.random.Poisson;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

/**
 * 
 * @author krokodil
 *
 */
public class SimDataCollector extends AbstractDataCollector {

	private WritableByteChannel hitsOut;
	private String mbid;
	private int card;
	private int pair;
	private char dom;
	private long clock;
	private long numericMBID;
	private RandomEngine rand = new MersenneTwister(new java.util.Date());
	private Poisson poissonRandom = new Poisson(1.0, rand);
	private int runLevel;
	private double rate;
	private boolean stopRunLoop;
	
	private final static Logger logger = Logger.getLogger(SimDataCollector.class);
	
	public SimDataCollector(DOMChannelInfo chanInfo, WritableByteChannel hitsOut)
	{
		this.mbid = chanInfo.mbid;
		this.card = chanInfo.card;
		this.pair = chanInfo.pair;
		this.dom  = chanInfo.dom;
		this.numericMBID = Long.parseLong(this.mbid, 16);
		this.hitsOut = hitsOut;
		runLevel  = 0;
	}
	
	public void setConfig(DOMConfiguration config) 
	{
		this.rate = config.getPulserRate();
	}

	public void signalConfigure() 
	{
		if (queryDaqRunLevel() > 2)
		{
			logger.error("Cannot configure DOM (even a simulated one) in state " + runLevel);
			throw new IllegalStateException();
		}
		try
		{
			Thread.sleep(500);
		}
		catch (InterruptedException intx)
		{
			intx.printStackTrace();
			logger.error("Interrupted sleep: " + intx.getMessage());
		}
		setRunLevel(1);
	}

	public void signalStartRun() 
	{
		if (queryDaqRunLevel() != 2)
		{
			logger.error("Cannot start run on DOM in state " + runLevel);
			throw new IllegalStateException();
		}
		setRunLevel(3);
	}

	public void signalStopRun() 
	{
		if (queryDaqRunLevel() != 4)
		{
			logger.error("Cannot stop run that is not running -- runLevel = " + runLevel);
			throw new IllegalStateException();
		}
		setRunLevel(5);
	}

	public synchronized int queryDaqRunLevel() 
	{
		return runLevel;
	}

	private synchronized void setRunLevel(int runLevel)
	{
		this.runLevel = runLevel;
	}
	
	@Override
	public void signalShutdown() 
	{
		stopRunLoop = true;
	}
	
	@Override 
	public void run()
	{
		stopRunLoop = false;
		
		runCore();
		try
		{
			hitsOut.write(StreamBinder.endOfStream());
		}
		catch (IOException iox)
		{
			iox.printStackTrace();
			logger.error(iox.getMessage());
		}
	}
	
	public void runCore()
	{
		
		Calendar now = new GregorianCalendar();
		Calendar startOfYear = new GregorianCalendar(now.get(Calendar.YEAR), 1, 1);
		long t0 = startOfYear.getTimeInMillis();
		
		// Give a random 'head-start' clock offset
		clock = rand.nextLong() >> 40;
		logger.info("Simulated DOM at " + card + "" + pair + "" + dom + " started at dom clock " + clock);

		long lastGenHit = 0L;

		try
		{
			// Simulate the device open latency
			Thread.sleep(1400);
	
			while (!stopRunLoop)
			{
				if (queryDaqRunLevel() == 1) 
				{
					// Simulate configure time
					Thread.sleep(500);
					setRunLevel(2);
				}
				else if (queryDaqRunLevel() == 3)
				{
					// go to start run
					Thread.sleep(20);
					setRunLevel(4);
					lastGenHit = System.currentTimeMillis();
				}
				else if (queryDaqRunLevel() == 4)
				{
					// now do the simple thing of creating N objects per second
					long currTime = System.currentTimeMillis();
					double dt = 1.0E-03 * (currTime - lastGenHit);
					double mu = dt * rate;
					int n = poissonRandom.nextInt(mu);
					logger.debug("Generated " + n + " events in interval " + lastGenHit + ":" + currTime);
					ArrayList<Long> eventTimes = new ArrayList<Long>(n);
					// generate n random times in the interval
					for (int i = 0; i < n; i++)
					{
						long rclk = 10000000L * (lastGenHit - t0) + (long) (1.0E+10 * rand.nextDouble() * dt);
						eventTimes.add(rclk);
					}
					// Order the event times
					Collections.sort(eventTimes);
					lastGenHit = currTime;
					for (int i = 0; i < n; i++)
					{
						final int recl = 80;
						ByteBuffer buf = ByteBuffer.allocate(recl);
						buf.putInt(recl);
						buf.putInt(2);
						buf.putLong(numericMBID);
						buf.putLong(0L);
						buf.putLong(eventTimes.get(i));
						buf.flip();
						hitsOut.write(buf);
					}
					if (n == 0) Thread.sleep(100);
				}
				else if (queryDaqRunLevel() == 5)
				{
					Thread.sleep(100);
					logger.info("Stopping data collection");
					setRunLevel(2);
				}
			}
		}
		catch (InterruptedException intx)
		{
			intx.printStackTrace();
			logger.error(intx.getMessage());
			return;
		}
		catch (IOException iox)
		{
			iox.printStackTrace();
			logger.error(iox.getMessage());
			return;
		}
		
	}

}

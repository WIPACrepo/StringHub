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
	private long t0;
	private long lastGenHit;
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
			logger.info("Configuring simulated DataCollector " + getName());
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
		Calendar startOfYear = new GregorianCalendar(now.get(Calendar.YEAR), 0, 1);
		t0 = startOfYear.getTimeInMillis();
		logger.debug("Start of year = " + t0);
		
		clock = 0L;
		logger.info("Simulated DOM at " + card + "" + pair + "" + dom + " started at dom clock " + clock);
		
		lastGenHit = 0L;

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
					long currTime = System.currentTimeMillis();
					int nHits = generateHits(currTime);
					int nMoni = generateMoni(currTime);
					if (nHits == 0) Thread.sleep(100);
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
	
	/**
	 * Contains all the yucky hit generation logic
	 * @return number of hits generated in the time interval
	 */
	private int generateHits(long currTime) throws IOException
	{
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
			final int recl = 112;
			ByteBuffer buf = ByteBuffer.allocate(recl);
			buf.putInt(recl);
			buf.putInt(2);
			buf.putLong(numericMBID);
			buf.putLong(0L);
			long utc = eventTimes.get(i);
			buf.putLong(utc);
			clock = utc / 500L;
			// Engineering header
			buf.putShort((short) 80).putShort((short) 2).put((byte) 0);
			// nFADC + ATWD readout config
			buf.put((byte) 0).put((byte) 3).put((byte) 0);
			// Trigger and spare field
			buf.put((byte) 2).put((byte) 0);
			// The clock word in 6 bytes
			buf.put((byte) ((clock >> 40) & 0xff));
			buf.put((byte) ((clock >> 32) & 0xff));
			buf.put((byte) ((clock >> 24) & 0xff));
			buf.put((byte) ((clock >> 16) & 0xff));
			buf.put((byte) ((clock >> 8) & 0xff));
			buf.put((byte) (clock & 0xff));
			for (int k = 0; k < 32; k++) buf.putShort((short) 138);
			buf.flip();
			logger.debug("Writing " + buf.remaining() + " byte hit at UTC = " + utc);
			hitsOut.write(buf);
		}
		return n;
	}
	
	private int generateMoni(long currTime) throws IOException
	{
		return 0;
	}

}

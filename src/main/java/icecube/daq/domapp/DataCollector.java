/* -*- mode: java; indent-tabs-mode:t; tab-width:4 -*- */

package icecube.daq.domapp;

import icecube.daq.bindery.StreamBinder;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.TimeCalib;
import icecube.daq.rapcal.LeadingEdgeRAPCal;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.util.UTC;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

/**
 * Document the class.
 * The output streams are in 'TestDAQ' format with the structure:
 * <table>
 * <tr><th>Offset</th><th>Size</th><th>Data</th></tr>
 * <tr><td>  0   </td><td>  4 </td><td>Record length</td></tr>
 * <tr><td>  4   </td><td>  4 </td><td>Record ID</td></tr>
 * <tr><td>  8   </td><td>  8 </td><td>Mainboard ID</td></tr>
 * <tr><td>  16  </td><td>  8 </td><td>Reserved - must be 0</td></tr>
 * <tr><td>  24  </td><td>  8 </td><td>UT timestamp</td></tr>
 * </table>
 * Supported records types are
 * <dl>
 * <dt>2</dt>
 * <dd>DOM engineering hit record</dd>
 * <dt>3</dt>
 * <dd>DOM delta-compressed hit records (including SLC hits)</dd>
 * <dt>102</dt>
 * <dd>DOM monitoring records</dd>
 * <dt>202</dt>
 * <dd>TCAL records</dd>
 * <dt>302</dt>
 * <dd>Supernova scaler records</dd>
 * </dl> 
 * @author krokodil
 *
 */
public class DataCollector extends AbstractDataCollector 
{
	private int 	card;
	private int 	pair;
	private char 	dom;
	private String 	mbid;
	private long	numericMBID;
	private IDOMApp app;
	private GPSInfo gps;
	private UTC		gpsOffset;
	private RAPCal  rapcal;
	private IDriver	driver;
	private boolean stop_thread;
	private WritableByteChannel hitsSink;
	private WritableByteChannel moniSink;
	private WritableByteChannel tcalSink;
	private WritableByteChannel supernovaSink;
	private DOMConfiguration	config;
	private int		runLevel;
	private static final Logger logger = Logger.getLogger(DataCollector.class);
	
	// TODO - replace these with properties-supplied constants
	// for now they are totally reasonable
	private long threadSleepInterval = 20;
	private long lastDataRead = 0;
	private long dataReadInterval = 20;
	private long lastMoniRead = 0;
	private long moniReadInterval = 1000;
	private long lastTcalRead = 0;
	private long tcalReadInterval = 1000;
	private long lastSupernovaRead = 0;
	private long supernovaReadInterval = 1000;
	
	private int rapcalExceptionCount = 0;
	private int validRAPCalCount;

	private int numHits = 0;
	private int numMoni = 0;
	private int numSupernova = 0;
	private int loopCounter = 0;
	private long lastDataUT = 0L;
	private long lastMoniUT = 0L;
	private long lastTcalUT = 0L;
	private long lastSupernovaUT = 0L;

	private ByteBuffer daqHeader;
	private static final long maxDataDelay = 300000000000L;

	public DataCollector(DOMChannelInfo chInfo, WritableByteChannel out) throws IOException, MessageException
	{
		this(chInfo.card, chInfo.pair, chInfo.dom, out, null, null, null);
	}

	public DataCollector(int card, int pair, char dom, WritableByteChannel out) throws IOException, MessageException
	{
		this(card, pair, dom, out, null, null, null);
	}
	
	public DataCollector(int card, int pair, char dom, 
			WritableByteChannel outHits, 
			WritableByteChannel outMoni, 
			WritableByteChannel outSupernova,
			WritableByteChannel outTcal) 
	throws IOException, MessageException 
	{
		this(card, pair, dom, outHits, outMoni, outSupernova, outTcal,
		     Driver.getInstance(), new LeadingEdgeRAPCal(50.0),
		     null);
	}
	
	public DataCollector(int card, int pair, char dom, 
			WritableByteChannel outHits, 
			WritableByteChannel outMoni, 
			WritableByteChannel outSupernova,
			WritableByteChannel outTcal,
			IDriver driver, RAPCal rapcal,
			IDOMApp app) 
	throws IOException, MessageException 
	{
		this.card = card;
		this.pair = pair;
		this.dom  = dom;
		this.hitsSink = outHits;
		//this.moniSink = null;
		//this.tcalSink = null;
		//this.supernovaSink = null;
		this.moniSink = outMoni;
		this.tcalSink = outTcal;
		this.supernovaSink = outSupernova;
		this.driver = driver;
		this.rapcal = rapcal;
		this.app = app;
		assert this.driver != null;
		assert this.rapcal != null;

		setName("DataCollector-" + card + "" + pair + dom);

		logger.debug("DC " + canonicalName() + " hitsSink = " + hitsSink);
		logger.debug("DC " + canonicalName() + " moniSink = " + moniSink);
		logger.debug("DC " + canonicalName() + " tcalSink = " + tcalSink);
		logger.debug("DC " + canonicalName() + " supernovaSink = " + supernovaSink);

		gps = null;
		
		runLevel = IDLE;
		gpsOffset = new UTC(0L);
		daqHeader = ByteBuffer.allocate(32);
	}

	public void close() 
	{ 
		if (app != null) app.close();
		try {
			
			if (hitsSink != null) {
				hitsSink.close();
				hitsSink = null;
			}
			if (moniSink != null) {
				moniSink.close();
				moniSink = null;
			}
			if (tcalSink != null) {
				tcalSink.close();
				tcalSink = null;
			}
			if (supernovaSink != null) {
				supernovaSink.close();
				supernovaSink = null;
			}
		} catch (IOException iox) {
			iox.printStackTrace();
			logger.error("Error closing pipe sinks: " + iox.getMessage());
		}
	}
	
    private String canonicalName()
    {
		return "[" + card + "" + pair + dom + "]";
    }

	public void setConfig(DOMConfiguration config) {
		this.config = config;
	}
	
	/**
	 * Applies the configuration in this.config to the DOM
	 * @throws MessageException
	 */
	private void configure() throws MessageException 
	{
		logger.info("Configuring DOM on " + canonicalName());
		long configT0 = System.currentTimeMillis();
		app.setMoniIntervals(config.getHardwareMonitorInterval(), config.getConfigMonitorInterval());
		if (config.isDeltaCompressionEnabled())
			app.setDeltaCompressionFormat();
		else
			app.setEngineeringFormat(config.getEngineeringFormat());
		if (config.getHV() >= 0) 
		{
			app.enableHV();
			app.setHV(config.getHV());
		} 
		else
		{
			app.disableHV();
		}
		for (byte dac_ch = 0; dac_ch < 16; dac_ch++) 
			app.writeDAC(dac_ch, config.getDAC(dac_ch));
		app.setMux(config.getMux());
		app.setTriggerMode(config.getTriggerMode());
		if (config.getPulserMode() == PulserMode.BEACON)
			app.pulserOff();
		else
			app.pulserOn();
		app.setPulserRate(config.getPulserRate());
		LocalCoincidenceConfiguration lc = config.getLC();
		app.setLCType(lc.getType());
		app.setLCMode(lc.getRxMode());
		app.setLCTx(lc.getTxMode());
		app.setLCSource(lc.getSource());
		app.setLCSpan(lc.getSpan());
		app.setLCWindow(lc.getPreTrigger(), lc.getPostTrigger());
		app.setCableLengths(lc.getCableLengthUp(), lc.getCableLengthDn());
		app.enableSupernova(config.getSupernovaDeadtime(), config.isSupernovaSpe());
		app.setScalerDeadtime(config.getScalerDeadtime());
		long configT1 = System.currentTimeMillis();
		logger.info("Finished DOM configuration - " + canonicalName() + 
					"; configuration took " + (configT1 - configT0) + " milliseconds.");
	}
	
	private long genericDataDispatch(
			int recl, int fmtid, long domClock, 
			ByteBuffer in, WritableByteChannel out) throws IOException 
	{
		daqHeader.clear();
		daqHeader.putInt(recl + 32).putInt(fmtid).putLong(numericMBID).putInt(0).putInt(0);
		long utc = rapcal.domToUTC(domClock).in_0_1ns();
		daqHeader.putLong(utc);
		daqHeader.flip();
		GatheringByteChannel g = (GatheringByteChannel) out;
		ByteBuffer bufferArray[] = new ByteBuffer[] { daqHeader, in };
		long nw = g.write(bufferArray);
		logger.debug("In DC " + canonicalName() + " - type = " + fmtid + 
					 " wrote " + nw + " bytes to " + out);
		return utc;
	}
	
	private void dataProcess(ByteBuffer in) throws IOException 
	{
		// TODO - I created a number of less-than-elegant hacks to
		// keep performance at acceptable level such as minimal
		// decoding of hit records.  This should be cleaned up.

		int buffer_limit = in.limit();
		
		// create records from aggregrate message data returned from DOMApp
		while (in.remaining() > 0) 
		{
			int pos = in.position();
			short len = in.getShort();
			short fmt = in.getShort();
			if (hitsSink != null) 
			{
				long domClock;
				switch (fmt)
				{
				case 1:
				case 2: // Engineering format data
					// strip out the clock word - advance pointer
					in.position(pos + 10);
					domClock = DOMAppUtil.decodeSixByteClock(in);
					in.position(pos);
					in.limit(in.position() + len);
					numHits++;
					lastDataUT = genericDataDispatch(len, 2, domClock, in, hitsSink);
					in.limit(buffer_limit);
					break;
				case 144: // Delta compressed data
					// It gets weird here - FPGA data written LITTLE_ENDIAN
					// Also must handle unpacking and applying clock context
					// to delta hits compressed in data block starting here. 
					in.order(ByteOrder.LITTLE_ENDIAN);
					int clkMSB = in.getShort();
					logger.debug("clkMSB: " + clkMSB);
					in.getShort();
					while (in.remaining() > 0)
					{
						in.mark();
						int hitSize = in.getInt() & 0x7ff;
						int clkLSB = in.getInt();
						logger.debug("hitsize: " + hitSize + " clkLSB: " + clkLSB);
						domClock = (((long) clkMSB) << 32) | (((long) clkLSB) & 0xffffffffL);
						in.reset();
						in.limit(in.position() + hitSize);
						numHits++;
						lastDataUT = genericDataDispatch(hitSize, 3, domClock, in, hitsSink);
						in.limit(buffer_limit);
					}
					in.order(ByteOrder.BIG_ENDIAN);
					break;
				}
			} 
			else 
			{
				// skip over this unknown record
				logger.warn("skipping over unknown record type " + fmt + " of " + len + " bytes.");
				in.position(in.position() + len - 4);
			}
		}
	}
	
	private void moniProcess(ByteBuffer in) throws IOException 
	{
		while (in.remaining() > 0)
		{
			// logger.debug("processing monitoring record - " + in.remaining() + " bytes remaining.");
			MonitorRecord monitor = MonitorRecordFactory.createFromBuffer(in);
			if (monitor instanceof AsciiMonitorRecord)
				logger.info(monitor.toString());
			if (moniSink != null) {
				numMoni++;
				lastMoniUT = genericDataDispatch(monitor.getLength(), 102, 
												 monitor.getClock(), 
												 monitor.getBuffer(), moniSink);
			}
		}
	}
	
	/**
	 * Process the TCAL data.  Please be aware of and excuse the awful
	 * coding here.  The TCAL reference time passed to the dispatcher is
	 * the DOM waveform receive time.  
	 * TODO The time transforms are hideous and need to be scrubbed.
	 * @param tcal
	 * @param gps
	 * @throws IOException
	 */
	private void tcalProcess(TimeCalib tcal, GPSInfo gps) throws IOException
	{
		if (tcalSink != null)
		{
			ByteBuffer buffer = ByteBuffer.allocate(500);
			tcal.writeUncompressedRecord(buffer);
			buffer.put(gps.getBuffer());
			buffer.flip();
			lastTcalUT = tcal.getDorTx().in_0_1ns();
			genericDataDispatch(buffer.remaining(), 202, tcal.getDomRx().in_0_1ns() / 250L, 
								buffer, tcalSink);
		}	
	}
	
	private void supernovaProcess(ByteBuffer in) throws IOException
	{
		while (in.remaining() > 0)
		{
			SupernovaPacket spkt = SupernovaPacket.createFromBuffer(in);
			if (supernovaSink != null) {
				numSupernova++;
				lastSupernovaUT = genericDataDispatch(spkt.getLength(), 302, spkt.getClock(), 
													  spkt.getBuffer(), supernovaSink);
			}
		}
	}
	
	/**
	 * Get the current RunLevel of this DataCollector
	 * @return integer-valued RunLevel code
	 * TODO make this an Enum
	 */
	public synchronized int queryDaqRunLevel() 
	{
		return runLevel;
	}
	
	private synchronized void setRunLevel(int level)
	{
		runLevel = level;
	}
	
	public synchronized void signalShutdown() {
		if (queryDaqRunLevel() > CONFIGURED) {
			logger.error("Attempt to shutdown collection thread in non-IDLE state.");
			throw new IllegalStateException();
		}
		stop_thread = true;
	}
	
	public synchronized void signalStartRun() {
		if (queryDaqRunLevel() != CONFIGURED) {
			logger.error("Attempt to start DOM in wrong state (" + queryDaqRunLevel() + ")");
			throw new IllegalStateException();
		}
		setRunLevel(STARTING);
	}

	/**
	 * Signal the DataCollector to terminate data collection.  Note that this may
	 * not be immediate.  Callers should poll the run state and otherwise not make 
	 * any assumptions on the run being stopped. 
	 */
	public synchronized void signalStopRun() {
		if (queryDaqRunLevel() != RUNNING) {
			logger.error("Attempt to stop run in non-RUNNING state.");
			throw new IllegalStateException();
		}
		setRunLevel(STOPPING);
	}
	
	public synchronized void signalConfigure() {
		if (queryDaqRunLevel() > CONFIGURED) {
			logger.error("Attempt to configure DOM in state above CONFIGURED.");
			throw new IllegalStateException();
		}
		setRunLevel(CONFIGURING);
	}
	
	public String toString() { return getName(); }
	

	private void execRapCal()
	{
		try 
		{
			gps = driver.readGPS(card);
			gpsOffset = gps.getOffset();
			TimeCalib tcal = driver.readTCAL(card, pair, dom);
			rapcal.update(tcal, gpsOffset);
			
			// TODO I don't know why this sleep is in here
			// take out - and discard by 6/2007 if no problems
			// Thread.sleep(100);
			validRAPCalCount++;
			
			if (queryDaqRunLevel() == RUNNING)
			{
				tcalProcess(tcal, gps);
			}
			
		} catch (RAPCalException rcex) {
			rapcalExceptionCount++;
			rcex.printStackTrace();
			logger.warn("Got RAPCal exception");
		} catch (GPSException gpsx) {
			gpsx.printStackTrace();
			logger.warn("Got GPS exception");
		} catch (IOException iox) {
			iox.printStackTrace();
			logger.warn(iox);
		} catch (InterruptedException intx) {
			intx.printStackTrace();
			logger.warn("Got interrupted exception");
		}		
	}
	
	/**
	 * The process is controlled by the runLevel state flag ...
	 * <dl>
	 * <dt>CONFIGURING (1)</dt>
	 * <dd>signal a configure needed - successful configure will propagate the state to CONFIGURED.</dd>
	 * <dt>CONFIGURED (2)</dt>
	 * <dd>the DOM is now configured and ready to start.</dd>
	 * <dt>STARTING (3)</dt>
	 * <dd>the DOM has received the start signal and is in process of starting run.</dd>
	 * <dt>RUNNING (4)</dt>
	 * <dd>the thread is acquiring data.</dd>
	 * <dt>STOPPING (5)</dt>
	 * <dd>the DOM has received the stop signal and is in process of returning to the CONFIGURED state.</dd>
	 * </dl>
	 */
	public void run() {
		
		logger.info("Begin data collection thread");
		try
		{
			runcore();
		}
		catch (Exception x)
		{
			x.printStackTrace();
			logger.error("Intercepted error in DataCollector runcore: " + x);
		}

		// clear interrupted flag if it is set
		interrupted();

		// HACK tell the caller that I am configured 
		setRunLevel(CONFIGURED);

		// Make sure eos is written
		try 
		{
			if (hitsSink != null) hitsSink.write(StreamBinder.endOfStream());
			if (moniSink != null) moniSink.write(StreamBinder.endOfMoniStream());
			if (tcalSink != null) tcalSink.write(StreamBinder.endOfTcalStream());
			if (supernovaSink != null) supernovaSink.write(StreamBinder.endOfSupernovaStream());
			logger.info("Wrote EOS to streams.");
		} 
		catch (IOException iox) 
		{
			logger.error(iox);
		}
		
	} /* END OF run() METHOD */
	
	/**
	 * This is a deeper run - basically I want a nice way of efficiently getting the stop
	 * signals written - a simple return to a wrapper which handles this seems best.  So
	 * the thread run method will handle that recovery process
	 */
	private void runcore() throws Exception
	{

		// Create a watcher timer
		Timer watcher = new Timer(getName() + "-timer");
		InterruptorTask intTask = new InterruptorTask(this);
		watcher.schedule(intTask, 10000L, 5000L);
 
		/* 
		 * Initialize the DOMApp - get things setup
		 */
		if (app == null)
		{
			// If app is null it implies the collector has deferred
			// opening of the DOR devfile to the thread.
			app = new DOMApp(this.card, this.pair, this.dom);
		}
		app.transitionToDOMApp();
		mbid = app.getMainboardID();
		numericMBID = Long.valueOf(mbid, 16).longValue();
		logger.info("Found DOM " + mbid + " running " + app.getRelease());

		/* 
		 * Quickly obtain 2 RAPCals prior to the start of data acquisition
		 * to ensure that first data packets may be time calibrated.
		 */
		for (int nTry = 0; nTry < 10 && validRAPCalCount < 2; nTry++) execRapCal();
		lastTcalRead = System.currentTimeMillis();
		
		/*
		 * Workhorse - the run loop
		 */
		logger.info("Entering run loop");

		while (!stop_thread && !interrupted()) 
		{
			long t = System.currentTimeMillis();
			boolean tired = true;

			// Ping the interruptor task
			intTask.ping();

			loopCounter++;

			/* Do TCAL and GPS -- this always runs regardless of the run state */
			if (t - lastTcalRead >= tcalReadInterval) 
			{
				logger.debug("Doing TCAL - runLevel is " + queryDaqRunLevel());
				lastTcalRead = t;
				execRapCal();
			}
			
			/* Check DATA & MONI - must be in running state (2) */
			if (queryDaqRunLevel() == RUNNING) 
			{
				if (t - lastDataRead >= dataReadInterval) 
				{
					lastDataRead = t;
					List<ByteBuffer> dataList = app.getData(5);
					for (ByteBuffer data : dataList)
						dataProcess(data);
					if (dataList.size() == 5) tired = false;

					/* 
					   Check for DOM readout lagging behind acquisition.
					   Reset acquisition if falls too far behind.  Use
					   the lastTcalUT for reference in case system clock
					   is incorrect.
					*/
					if (lastDataUT != 0L && (lastTcalUT - lastDataUT > maxDataDelay)) {
						logger.warn("DOM data lag detected: (" + 
									lastTcalUT + ", " +
									lastDataUT + ").  Resetting ACQ.");
						app.endRun();
						app.beginRun();
						logger.warn("ACQ restarted.");
					}
				}
				if (t - lastMoniRead >= moniReadInterval) 
				{
					lastMoniRead = t;
					ByteBuffer moni = app.getMoni();
					if (moni.remaining() > 0) 
					{
						moniProcess(moni);
						tired = false;
					}
				}
				if (t - lastSupernovaRead > supernovaReadInterval)
				{
					lastSupernovaRead = t;
					while (true)
						{
							ByteBuffer sndata = app.getSupernova();
							int sn_data_length = sndata.remaining();
							if (sn_data_length > 0)
								{
									supernovaProcess(sndata);
									tired = false;
								}
							if (sn_data_length != 11) break;
						}
				}
			} 
			else if (queryDaqRunLevel() == CONFIGURING) 
			{
				/* Need to handle a configure */
				logger.info("Got CONFIGURE signal.");
				configure();
				logger.info("DOM is configured.");
				setRunLevel(CONFIGURED);
			} 
			else if (queryDaqRunLevel() == STARTING) 
			{
				logger.info("Got START RUN signal " + canonicalName());
				System.out.println("Got START RUN signal " + canonicalName());
				app.beginRun();
				logger.info("DOM is running.");
				setRunLevel(RUNNING);
			} 
			else if (queryDaqRunLevel() == STOPPING) 
			{
				logger.info("Got STOP RUN signal " + canonicalName());
				System.out.println("Got STOP RUN signal " + canonicalName());
				app.endRun();
				// Write the end-of-stream token
				if (hitsSink != null) hitsSink.write(StreamBinder.endOfStream());
				if (moniSink != null) moniSink.write(StreamBinder.endOfMoniStream());
				if (tcalSink != null) tcalSink.write(StreamBinder.endOfTcalStream());
				if (supernovaSink != null) supernovaSink.write(StreamBinder.endOfSupernovaStream());
				setRunLevel(CONFIGURED);
			}
			
			if (tired) 
			{
				try 
				{
					Thread.sleep(threadSleepInterval);
				} 
				catch (InterruptedException intx) 
				{
					logger.warn("Interrupted.");
				}
			}
		} /* END RUN LOOP */
	} /* END METHOD */

	public long getNumHits() { return numHits; }
	public long getNumMoni() { return numMoni; }
	public long getNumTcal() { return validRAPCalCount; }
	public long getNumSupernova() { return numSupernova; }
	public long getAcquisitionLoopCount() { return loopCounter; }

	/**
	 * A watchdog timer task to make sure data stream does not die.
	 */
	class InterruptorTask extends TimerTask 
	{
		Thread thread;
		boolean pinged;
		
		InterruptorTask(Thread thread) 
		{
			this.thread = thread;
			this.pinged = false;
		}
		
		public void run()
		{
			if (!pinged) thread.interrupt();
			pinged = false;
		}
		
		synchronized void ping()
		{
			pinged = true;
		}
	}
}


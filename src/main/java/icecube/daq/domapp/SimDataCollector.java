package icecube.daq.domapp;

import cern.jet.random.Poisson;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.bindery.StreamBinder;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.util.StringHubAlert;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.log4j.Logger;

/**
 *
 * Simulator for StringHub DataCollector.  This simulation is fairly basic at the moment but 
 * is nevertheless useful as a generator to provide bona-fide inputs to the downstream DAQ
 * which is quite oblivious of the sham going on under its nose.
 * 
 * The SimDataCollector produces Poissonian generated hits at a rate specified in the supplied
 * configuration by the simNoiseRate parameter - typically this will come from an XML config
 * with tag of same name.
 * 
 * @author krokodil
 *
 */
public class SimDataCollector extends AbstractDataCollector
{
    private BufferConsumer  hitsConsumer;
    private BufferConsumer  moniConsumer;
    private BufferConsumer  tcalConsumer;
    private BufferConsumer  scalConsumer;
    private long            clock;
    private long            t0;
    private long            lastGenHit;           // right edge of previous hit generation time window
    private long            lastMoni;             // last moni record
    private long            lastTcal;             // last time a Tcal was generated
    private long            lastSupernova;        // last time a SN record was generated in 1e-7sec unit (sorry...)
    private long            lastBeacon;           // keep track of the beacon hits ...
    private long            numericMBID;
    private RandomEngine    rand = new MersenneTwister(new java.util.Date());
    private Poisson         poissonRandom = new Poisson(1.0, rand);
    private double          rate;
    private double          hlcFrac = 1.0;
    private boolean         stopRunLoop;
    private long            numHits;
    private long            loopCounter;
    private double          pulserRate = 1.0;
    private volatile long   runStartUT = 0L;
	private	boolean 		snSigEnabled;					
	private double 			snDistance;				
	private boolean 		effVolumeEnabled;		

    private Thread thread;
	private double[] avgSnSignal;
	private double[] effVolumeScaling;

    private boolean isAmanda;

    private static final Logger logger = Logger.getLogger(SimDataCollector.class);

    public SimDataCollector(int card, int pair, char dom, double[] avgSnSignal,
			double[] effVolumeScaling) {
		super(card, pair, dom);
		this.avgSnSignal = avgSnSignal;
		this.effVolumeScaling = effVolumeScaling;
	}

	public SimDataCollector(DOMChannelInfo chanInfo, BufferConsumer hitsConsumer)
    {
        this(chanInfo, null, hitsConsumer, null, null, null, false);
    }

    public SimDataCollector(DOMChannelInfo chanInfo,
                            DOMConfiguration config,
                            BufferConsumer hitsConsumer,
                            BufferConsumer moniConsumer,
                            BufferConsumer scalConsumer,
                            BufferConsumer tcalConsumer,
                            boolean isAmanda
                            )
    {
        super(chanInfo.card, chanInfo.pair, chanInfo.dom);
        this.mbid = chanInfo.mbid;
        this.numericMBID  = Long.parseLong(this.mbid, 16);
        this.hitsConsumer = hitsConsumer;
        this.moniConsumer = moniConsumer;
        this.scalConsumer = scalConsumer;
        this.tcalConsumer = tcalConsumer;
        this.isAmanda     = isAmanda;
        runLevel          = RunLevel.IDLE;
        numHits           = 0;
        loopCounter       = 0;
        if (config != null) {
            rate = config.getSimNoiseRate();
            pulserRate = config.getPulserRate();
            hlcFrac = config.getSimHLCFrac();
            snSigEnabled = config.isSnSigEnabled();
            snDistance = config.getSnDistance();
            effVolumeEnabled = config.isEffVolumeEnabled();
        }
        thread = new Thread(this, "SimDataCollector-" + card + "" + pair + dom);
        thread.start();
    }

    public void close()
    {
        // do nothing
    }

    public void signalShutdown()
    {
        if (logger.isInfoEnabled()) {
            logger.info("Shutting down data collector [" + card + "" + pair +
                        "" + dom + "]");
        }
        setRunStopFlag(true);
    }

    public synchronized void setRunStopFlag(boolean val)
    {
        stopRunLoop = val;
    }

    public synchronized boolean keepRunning()
    {
        return !stopRunLoop;
    }


    public void run()
    {
        setRunStopFlag(false);

        logger.info("Entering run loop.");

        runCore();

        logger.info("Exited runCore() loop.");

        try {
            ByteBuffer otrava = MultiChannelMergeSort.eos(numericMBID);
            if (hitsConsumer != null) hitsConsumer.consume(otrava.asReadOnlyBuffer());
            if (moniConsumer != null) moniConsumer.consume(otrava.asReadOnlyBuffer());
            if (tcalConsumer != null) tcalConsumer.consume(otrava.asReadOnlyBuffer());
            if (scalConsumer != null && !isAmanda) scalConsumer.consume(otrava.asReadOnlyBuffer());
        } catch (IOException iox) {
            iox.printStackTrace();
            logger.error(iox.getMessage());
        }
    }

    private boolean fakeAlert()
    {
        final File flagFile = new File("/tmp/dom.alert");
        if (!flagFile.exists()) {
            return false;
        }

        StringHubAlert.sendDOMAlert(alerter, "Fake DOM alert", "Fake DOM alert",
                                    card, pair, dom, mbid, name, major, minor);
        return true;
    }

    public void runCore()
    {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        t0 = cal.getTimeInMillis();
        
        if (logger.isDebugEnabled()) logger.debug("Start of year = " + t0);

        clock = 0L;
        if (logger.isInfoEnabled()) {
            logger.info("Simulated DOM at " + card + "" + pair + "" + dom +
                        " started at dom clock " + clock);
        }

        lastGenHit = 0L;
        lastBeacon = 0L;

        try {
            // Simulate the device open latency
            Thread.sleep(1400);

            boolean snStopped = false;

            while (keepRunning()) {
                boolean needSomeSleep = true;

                loopCounter++;

                switch (getRunLevel()) {
                case CONFIGURING:
                    // Simulate configure time
                    Thread.sleep(500);
                    setRunLevel(RunLevel.CONFIGURED);
                    logger.info("DOM is now configured.");
                    break;
                case STARTING:
                    // go to start run
                    Thread.sleep(20);
                    storeRunStartTime();
                    setRunLevel(RunLevel.RUNNING);
                    long t = System.currentTimeMillis();
                    lastGenHit = t;
                    lastBeacon = t;
                    lastMoni   = t;
                    lastTcal   = t;
                    lastSupernova = t*10000L; 	// strange units to get precision
                    break;
                case STARTING_SUBRUN:
                    // go to start run
                    Thread.sleep(1000L);
                    storeRunStartTime();
                    setRunLevel(RunLevel.RUNNING);
                    break;
                case RUNNING:
                    long currTime = System.currentTimeMillis();
                    int nHits = generateHits(currTime);
                    generateMoni(currTime);
                    generateTCal(currTime);
                    if (!isAmanda) {
                        generateSupernova(currTime);
                    } else if (!snStopped) {
                        logger.error("Immediately stopping supernova channel");
                        try {
                            ByteBuffer otrava =
                                MultiChannelMergeSort.eos(numericMBID);
                            if (scalConsumer != null)
                                scalConsumer.consume(otrava.asReadOnlyBuffer());
                        } catch (IOException iox) {
                            iox.printStackTrace();
                            logger.error("Couldn't stop supernova channel",
                                         iox);
                        }
                        snStopped = true;
                    }
                    if (fakeAlert()) {
                        setRunLevel(RunLevel.ZOMBIE);
                        setRunStopFlag(true);
                    }
                    if (nHits > 0) needSomeSleep = false;
                    break;
                case STOPPING:
                    Thread.sleep(100);
                    logger.info("Stopping data collection");
                    setRunLevel(RunLevel.CONFIGURED);
                    return;
                }

                // CPU reduction action
                if (needSomeSleep) Thread.sleep(100);
            }
        } catch (InterruptedException intx) {
            intx.printStackTrace();
            logger.error(intx.getMessage());
            return;
        } catch (IOException iox) {
            iox.printStackTrace();
            logger.error(iox.getMessage());
            return;
        }
    }

    public long getRunStartTime()
    {
        return runStartUT;
    }

    private void storeRunStartTime()
    {
        runStartUT =  (System.currentTimeMillis() - t0) * 10000000L;
    }

    private int generateTCal(long currTime) throws IOException
    {
        final short[] tcalWf = new short[] {
            501, 500, 503, 505, 499, 499, 505, 500,
            501, 500, 500, 502, 500, 500, 503, 499,
            500, 499, 497, 499, 500, 500, 501, 500,
            501, 501, 500, 499, 500, 500, 501, 500,
            513, 550, 616, 690, 761, 819, 864, 898,
            925, 949, 958, 929, 856, 751, 630, 518,
            424, 346, 277, 207, 156, 137, 148,   0,
            0,   0,   0,   0,   0,   0,   0,   0
        };

        if (currTime - lastTcal < 1000L) return 0;
        lastTcal = currTime;
        final int tcalRecl = 324;
        ByteBuffer buf = ByteBuffer.allocate(tcalRecl);
        long utc = (currTime - t0) * 10000000L;
        buf.putInt(tcalRecl).putInt(202).putLong(numericMBID).putLong(0L).putLong(utc);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 224).putShort((short) 1);
        long dorTx = utc / 500L;
        long dorRx = dorTx + 100000L;
        long domRx = dorTx + 49000L;
        long domTx = dorTx + 51000L;
        buf.putLong(dorTx).putLong(dorRx);
        for (int i = 0; i < 64; i++) buf.putShort(tcalWf[i]);
        buf.putLong(domRx).putLong(domTx);
        for (int i = 0; i < 64; i++) buf.putShort(tcalWf[i]);
        buf.flip();
        if (tcalConsumer != null) tcalConsumer.consume(buf);
        return 1;
    }

    private int generateSupernova(long currTime) throws IOException {
        if (currTime - lastSupernova/10000L < 1000L) return 0;
        // Simulate SN wrap-around
        if (currTime - lastSupernova/10000L > 10000L) {
            lastSupernova = (currTime - 10000L)*10000L;
            logger.warn("Buffer overflow in SN record channel: " + mbid);
        }
        int dtms = (int) (currTime - lastSupernova/10000L);
        int nsn = dtms * 10000 / 16384 /4*4;      // restrict utc advancing to 4*1.6384 ms increments only
        // sn Data Challenge
		long runStartMilli = getRunStartTime()/10000000L + t0;
		long hundredSec = 100000L;
		long tenMinutes = 6*hundredSec;
//		long snStartTime = ((runStartMilli/hundredSec)+1)*hundredSec;	// start sn within the next 100 sec (in ms)
		long snStartTime = ((runStartMilli/hundredSec)+1)*hundredSec + tenMinutes;	// start sn within the next 100 sec (in ms) + some specified time
		double effVol = 1.;
		if (effVolumeEnabled) {
			int domZNum = 8*card + 2*pair + (2-(dom-'A'));	// dom = 'A' -> 2, dom = 'B' -> 1
			effVol = effVolumeScaling(domZNum);
		}
		//	
        short recl = (short) (10 + nsn);
        ByteBuffer buf = ByteBuffer.allocate(recl+32);
        long utc = lastSupernova*1000L - t0 * 10000000L;  // utc is in 1e-10sec
        long clk = utc / 250L;
        
//        if (logger.isDebugEnabled())
//        {
//            logger.debug("runStartMilli: " + runStartMilli + " MBID: " + mbid + " lastSupernova: " + lastSupernova + " UTC: " + utc + " # SN: " + nsn);
//        }
        
        buf.putInt(recl+32);
        buf.putInt(302);
        buf.putLong(numericMBID);
        buf.putLong(0L);
        buf.putLong(utc);
        buf.putShort(recl);
        buf.putShort((short) 300);
        buf.put((byte) ((clk >> 40) & 0xff));
        buf.put((byte) ((clk >> 32) & 0xff));
        buf.put((byte) ((clk >> 24) & 0xff));
        buf.put((byte) ((clk >> 16) & 0xff));
        buf.put((byte) ((clk >>  8) & 0xff));
        buf.put((byte) clk);
        for (int i = 0; i < nsn; i++) {
		    double snRate = 0.;
		    long binTime = lastSupernova/10000L + i*10000/16384;  // time of the i'th nsn bin in ms
//		    if ((nsnSig+i+1 > 0) && (nsnSig+i < maxnsnSig)) {
    		if (snSigEnabled) {
    			if (binTime > snStartTime) {
    				if (binTime < snStartTime + 16.384*916) {  	// There are 916 bins of 16.384 ms in the signal model below			
		    			int snBin = (int) ((binTime - snStartTime)*10000/16384);
//    			        if (logger.isDebugEnabled()) {
//    			            logger.debug("snBin: " + snBin);
//    			        }
		    			snRate = snSignalPerDom(snBin)*effVol*(10./snDistance)*(10./snDistance);
		    		}
		    	}
		    }
		    int scaler = poissonRandom.nextInt(300 * 0.0016384) + poissonRandom.nextInt(snRate);
		    if (scaler > 15) scaler = 15;
		    buf.put((byte) scaler);
        }
        buf.flip();

        lastSupernova = lastSupernova + nsn*16384;
        
        if (scalConsumer != null) scalConsumer.consume(buf);
        return 1;
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
        numHits += n;
//         if (logger.isDebugEnabled())
//             logger.debug("Generated " + n + " events in interval " + lastGenHit + ":" + currTime);
        ArrayList<Long> eventTimes = new ArrayList<Long>(n);
        // generate n random times in the interval
        for (int i = 0; i < n; i++) {
            long rclk = 10000000L * (lastGenHit - t0) + (long) (1.0E+10 * rand.nextDouble() * dt);
            eventTimes.add(rclk);
        }

        while (true) {
            long nextBeacon = lastBeacon + (long) (1000.0 * pulserRate);
            if (nextBeacon > currTime) break;
            eventTimes.add(10000000L * (nextBeacon - t0));
            lastBeacon = nextBeacon;
        }
        // Order the event times
        Collections.sort(eventTimes);
        lastGenHit = currTime;
        for (int i = 0; i < n; i++) {
            final int recl = 54;
            ByteBuffer buf = ByteBuffer.allocate(recl);
            buf.putInt(recl);
            buf.putInt(3);
            buf.putLong(numericMBID);
            buf.putLong(0L);
            long utc = eventTimes.get(i);
            buf.putLong(utc);
            clock = utc / 500L;
            buf.putShort((short) 0x01).putShort((short) 0x01).putShort((short) 0x00);
            // the actual un-translated domclock goes here
            buf.putLong(clock);
            // simulation of the lcBits happens here
            int lcBits = 0;
            if (rand.nextDouble() < hlcFrac) lcBits = 0x30000;
            int word1 = 0x9004c00c | lcBits;
            int word3 = 0x00000000;
            buf.putInt(word1).putInt(word3);
            buf.flip();
//            if (logger.isDebugEnabled())
//                logger.debug("Writing " + buf.remaining() + " byte hit at UTC = " + utc);
            if (hitsConsumer != null) hitsConsumer.consume(buf);
        }
        return n;
    }

    private int generateMoni(long currTime) throws IOException
    {
        if (currTime - lastMoni < 1000) return 0;
        // Generate an ASCII time record
        ByteBuffer moniBuf = ByteBuffer.allocate(100);
        String txt = "Hi Artur - I am a DOM monitoring record.";
        int recl = 42 + txt.length();
        moniBuf.putInt(recl);			// record length
        moniBuf.putInt(102);			// special moni code
        moniBuf.putLong(numericMBID);	// mbid as 8-byte long
        moniBuf.putLong(0L);
        long utc = (currTime - t0) * 10000000L;
        moniBuf.putLong(utc);
        clock = utc / 500L;
        moniBuf.putShort((short) (10+txt.length()));
        moniBuf.putShort((short) 0xCB);
        moniBuf.put((byte) ((clock >> 40) & 0xff));
        moniBuf.put((byte) ((clock >> 32) & 0xff));
        moniBuf.put((byte) ((clock >> 24) & 0xff));
        moniBuf.put((byte) ((clock >> 16) & 0xff));
        moniBuf.put((byte) ((clock >> 8) & 0xff));
        moniBuf.put((byte) (clock & 0xff));
        moniBuf.put(txt.getBytes());
        moniBuf.flip();
        if (moniConsumer != null) moniConsumer.consume(moniBuf);
        lastMoni = currTime;
        return 1;
    }

    public long getNumHits() {
        return numHits;
    }

    public long getNumMoni() {
        return 0;
    }

    public long getNumTcal() {
        return 0;
    }

    public long getNumSupernova() {
        return 0;
    }

    public long getAcquisitionLoopCount() {
        return loopCounter;
    }

    public void start()
    {
        // TODO Auto-generated method stub
    }
    
    /**
     * Contains effective volume scaling factor per DOM for single photon detection
     * at 60 DOM locations starting at +500m going down by 17m, down to -503m.
	 * @param domZNum (0 to 59)
     * @return effective volume scaling factor for each DOM depth
     */
    public double effVolumeScaling(int domZNum) {
	final double[] effVolumeScale = {
	    0.861, 0.901, 0.952, 0.988, 0.973, 0.918, 0.851, 0.802,
	    0.825, 0.867, 0.903, 0.950, 0.992, 1.018, 1.005, 0.953,
	    0.879, 0.838, 0.855, 0.909, 0.965, 0.993, 0.993, 0.966,
	    0.927, 0.923, 0.957, 1.003, 1.025, 1.012, 0.965, 0.891,
	    0.791, 0.679, 0.588, 0.544, 0.617, 0.831, 0.970, 1.054,
	    1.077, 1.088, 1.083, 1.094, 1.123, 1.166, 1.206, 1.232,
	    1.238, 1.225, 1.202, 1.188, 1.197, 1.241, 1.279, 1.302,
	    1.304, 1.294, 1.274, 1.250, 0.000, 0.000, 0.000, 0.000
	};
	return effVolumeScale[domZNum-1];
    }

    /**
     * Contains supernova signal extracted from Livermore paper in 10*1.6384ms bins for a 10 kpc supernova.
     * (we only have luminosity in 0.1sec resolution).  The entire signal has 916 entries, lasting 15 seconds.
	 * @param nsnSigBin: the 1.6384ms time bin to set
     * @return expected supernova signal per DOM in the 1.6384ms bin
     */
   public double snSignalPerDom(int nsnSigBin) {
     	double[] avgSnSignalPerDom = {
    			0.555415, 0.555415, 0.555415, 0.555415, 0.555415, 0.555415, 0.555415,
    			1.97197, 1.97197, 1.97197, 1.97197, 1.97197, 1.97197, 2.05904, 2.05904,
    			2.05904, 2.05904, 2.05904, 2.05904, 1.42308, 1.42308, 1.42308, 1.42308,
    			1.42308, 1.42308, 0.812823, 0.812823, 0.812823, 0.812823, 0.812823,
    			0.812823, 0.687798, 0.687798, 0.687798, 0.687798, 0.687798, 0.687798,
    			0.644868, 0.644868, 0.644868, 0.644868, 0.644868, 0.644868, 0.599942,
    			0.599942, 0.599942, 0.599942, 0.599942, 0.599942, 0.561448, 0.561448,
    			0.561448, 0.561448, 0.561448, 0.561448, 0.532783, 0.532783, 0.532783,
    			0.532783, 0.532783, 0.532783, 0.532783, 0.509226, 0.509226, 0.509226,
    			0.509226, 0.509226, 0.509226, 0.485543, 0.485543, 0.485543, 0.485543,
    			0.485543, 0.485543, 0.460075, 0.460075, 0.460075, 0.460075, 0.460075,
    			0.460075, 0.436051, 0.436051, 0.436051, 0.436051, 0.436051, 0.436051,
    			0.385165, 0.385165, 0.385165, 0.385165, 0.385165, 0.385165, 0.325286,
    			0.325286, 0.325286, 0.325286, 0.325286, 0.325286, 0.285579, 0.285579,
    			0.285579, 0.285579, 0.285579, 0.285579, 0.257917, 0.257917, 0.257917,
    			0.257917, 0.257917, 0.257917, 0.236709, 0.236709, 0.236709, 0.236709,
    			0.236709, 0.236709, 0.223189, 0.223189, 0.223189, 0.223189, 0.223189,
    			0.223189, 0.223189, 0.21087, 0.21087, 0.21087, 0.21087, 0.21087,
    			0.21087, 0.204817, 0.204817, 0.204817, 0.204817, 0.204817, 0.204817,
    			0.200394, 0.200394, 0.200394, 0.200394, 0.200394, 0.200394, 0.196936,
    			0.196936, 0.196936, 0.196936, 0.196936, 0.196936, 0.194064, 0.194064,
    			0.194064, 0.194064, 0.194064, 0.194064, 0.188852, 0.188852, 0.188852,
    			0.188852, 0.188852, 0.188852, 0.178895, 0.178895, 0.178895, 0.178895,
    			0.178895, 0.178895, 0.166188, 0.166188, 0.166188, 0.166188, 0.166188,
    			0.166188, 0.155612, 0.155612, 0.155612, 0.155612, 0.155612, 0.155612,
    			0.155612, 0.142742, 0.142742, 0.142742, 0.142742, 0.142742, 0.142742,
    			0.129152, 0.129152, 0.129152, 0.129152, 0.129152, 0.129152, 0.117211,
    			0.117211, 0.117211, 0.117211, 0.117211, 0.117211, 0.103214, 0.103214,
    			0.103214, 0.103214, 0.103214, 0.103214, 0.0800614, 0.0800614, 0.0800614,
    			0.0800614, 0.0800614, 0.0800614, 0.0935177, 0.0935177, 0.0935177,
    			0.0935177, 0.0935177, 0.0935177, 0.0894246, 0.0894246, 0.0894246,
    			0.0894246, 0.0894246, 0.0894246, 0.086065, 0.086065, 0.086065, 0.086065,
    			0.086065, 0.086065, 0.0823025, 0.0823025, 0.0823025, 0.0823025,
    			0.0823025, 0.0823025, 0.0800976, 0.0800976, 0.0800976, 0.0800976,
    			0.0800976, 0.0800976, 0.0800976, 0.0785381, 0.0785381, 0.0785381,
    			0.0785381, 0.0785381, 0.0785381, 0.0773815, 0.0773815, 0.0773815,
    			0.0773815, 0.0773815, 0.0773815, 0.0761179, 0.0761179, 0.0761179,
    			0.0761179, 0.0761179, 0.0761179, 0.0744786, 0.0744786, 0.0744786,
    			0.0744786, 0.0744786, 0.0744786, 0.0735148, 0.0735148, 0.0735148,
    			0.0735148, 0.0735148, 0.0735148, 0.072854, 0.072854, 0.072854, 0.072854,
    			0.072854, 0.072854, 0.0721623, 0.0721623, 0.0721623, 0.0721623,
    			0.0721623, 0.0721623, 0.0711182, 0.0711182, 0.0711182, 0.0711182,
    			0.0711182, 0.0711182, 0.0706666, 0.0706666, 0.0706666, 0.0706666,
    			0.0706666, 0.0706666, 0.0710069, 0.0710069, 0.0710069, 0.0710069,
    			0.0710069, 0.0710069, 0.0710069, 0.0709269, 0.0709269, 0.0709269,
    			0.0709269, 0.0709269, 0.0709269, 0.0716796, 0.0716796, 0.0716796,
    			0.0716796, 0.0716796, 0.0716796, 0.0722537, 0.0722537, 0.0722537,
    			0.0722537, 0.0722537, 0.0722537, 0.0719262, 0.0719262, 0.0719262,
    			0.0719262, 0.0719262, 0.0719262, 0.0698165, 0.0698165, 0.0698165,
    			0.0698165, 0.0698165, 0.0698165, 0.0682488, 0.0682488, 0.0682488,
    			0.0682488, 0.0682488, 0.0682488, 0.066171, 0.066171, 0.066171, 0.066171,
    			0.066171, 0.066171, 0.0640206, 0.0640206, 0.0640206, 0.0640206,
    			0.0640206, 0.0640206, 0.0627915, 0.0627915, 0.0627915, 0.0627915,
    			0.0627915, 0.0627915, 0.0627915, 0.061848, 0.061848, 0.061848, 0.061848,
    			0.061848, 0.061848, 0.0609487, 0.0609487, 0.0609487, 0.0609487,
    			0.0609487, 0.0609487, 0.0601714, 0.0601714, 0.0601714, 0.0601714,
    			0.0601714, 0.0601714, 0.0588215, 0.0588215, 0.0588215, 0.0588215,
    			0.0588215, 0.0588215, 0.058298, 0.058298, 0.058298, 0.058298, 0.058298,
    			0.058298, 0.0575611, 0.0575611, 0.0575611, 0.0575611, 0.0575611,
    			0.0575611, 0.0572319, 0.0572319, 0.0572319, 0.0572319, 0.0572319,
    			0.0572319, 0.0564886, 0.0564886, 0.0564886, 0.0564886, 0.0564886,
    			0.0564886, 0.0562373, 0.0562373, 0.0562373, 0.0562373, 0.0562373,
    			0.0562373, 0.0553513, 0.0553513, 0.0553513, 0.0553513, 0.0553513,
    			0.0553513, 0.0553513, 0.0536138, 0.0536138, 0.0536138, 0.0536138,
    			0.0536138, 0.0536138, 0.0519352, 0.0519352, 0.0519352, 0.0519352,
    			0.0519352, 0.0519352, 0.0507732, 0.0507732, 0.0507732, 0.0507732,
    			0.0507732, 0.0507732, 0.0490238, 0.0490238, 0.0490238, 0.0490238,
    			0.0490238, 0.0490238, 0.0480949, 0.0480949, 0.0480949, 0.0480949,
    			0.0480949, 0.0480949, 0.0474869, 0.0474869, 0.0474869, 0.0474869,
    			0.0474869, 0.0474869, 0.0468252, 0.0468252, 0.0468252, 0.0468252,
    			0.0468252, 0.0468252, 0.0457643, 0.0457643, 0.0457643, 0.0457643,
    			0.0457643, 0.0457643, 0.0450419, 0.0450419, 0.0450419, 0.0450419,
    			0.0450419, 0.0450419, 0.0443859, 0.0443859, 0.0443859, 0.0443859,
    			0.0443859, 0.0443859, 0.0443859, 0.0433807, 0.0433807, 0.0433807,
    			0.0433807, 0.0433807, 0.0433807, 0.0423555, 0.0423555, 0.0423555,
    			0.0423555, 0.0423555, 0.0423555, 0.0416483, 0.0416483, 0.0416483,
    			0.0416483, 0.0416483, 0.0416483, 0.040534, 0.040534, 0.040534, 0.040534,
    			0.040534, 0.040534, 0.0400467, 0.0400467, 0.0400467, 0.0400467,
    			0.0400467, 0.0400467, 0.0397545, 0.0397545, 0.0397545, 0.0397545,
    			0.0397545, 0.0397545, 0.0395149, 0.0395149, 0.0395149, 0.0395149,
    			0.0395149, 0.0395149, 0.0393485, 0.0393485, 0.0393485, 0.0393485,
    			0.0393485, 0.0393485, 0.0390964, 0.0390964, 0.0390964, 0.0390964,
    			0.0390964, 0.0390964, 0.0390964, 0.0391911, 0.0391911, 0.0391911,
    			0.0391911, 0.0391911, 0.0391911, 0.0394126, 0.0394126, 0.0394126,
    			0.0394126, 0.0394126, 0.0394126, 0.0397086, 0.0397086, 0.0397086,
    			0.0397086, 0.0397086, 0.0397086, 0.0401501, 0.0401501, 0.0401501,
    			0.0401501, 0.0401501, 0.0401501, 0.0404126, 0.0404126, 0.0404126,
    			0.0404126, 0.0404126, 0.0404126, 0.0404878, 0.0404878, 0.0404878,
    			0.0404878, 0.0404878, 0.0404878, 0.041104, 0.041104, 0.041104, 0.041104,
    			0.041104, 0.041104, 0.0413326, 0.0413326, 0.0413326, 0.0413326,
    			0.0413326, 0.0413326, 0.0414852, 0.0414852, 0.0414852, 0.0414852,
    			0.0414852, 0.0414852, 0.0416381, 0.0416381, 0.0416381, 0.0416381,
    			0.0416381, 0.0416381, 0.0416381, 0.0417708, 0.0417708, 0.0417708,
    			0.0417708, 0.0417708, 0.0417708, 0.0412428, 0.0412428, 0.0412428,
    			0.0412428, 0.0412428, 0.0412428, 0.0407199, 0.0407199, 0.0407199,
    			0.0407199, 0.0407199, 0.0407199, 0.0403126, 0.0403126, 0.0403126,
    			0.0403126, 0.0403126, 0.0403126, 0.0399835, 0.0399835, 0.0399835,
    			0.0399835, 0.0399835, 0.0399835, 0.0394766, 0.0394766, 0.0394766,
    			0.0394766, 0.0394766, 0.0394766, 0.0389768, 0.0389768, 0.0389768,
    			0.0389768, 0.0389768, 0.0389768, 0.0385711, 0.0385711, 0.0385711,
    			0.0385711, 0.0385711, 0.0385711, 0.0379614, 0.0379614, 0.0379614,
    			0.0379614, 0.0379614, 0.0379614, 0.0377549, 0.0377549, 0.0377549,
    			0.0377549, 0.0377549, 0.0377549, 0.0377549, 0.0374462, 0.0374462,
    			0.0374462, 0.0374462, 0.0374462, 0.0374462, 0.0375151, 0.0375151,
    			0.0375151, 0.0375151, 0.0375151, 0.0375151, 0.0375151, 0.0375151,
    			0.0375151, 0.0375151, 0.0375151, 0.0375151, 0.0375151, 0.0375151,
    			0.0375151, 0.0375151, 0.0375151, 0.0375151, 0.0375151, 0.0375151,
    			0.0375151, 0.0375151, 0.0375151, 0.0375151, 0.0375151, 0.0375151,
    			0.0375151, 0.0375151, 0.0375151, 0.0375151, 0.0375498, 0.0375498,
    			0.0375498, 0.0375498, 0.0375498, 0.0375498, 0.0375498, 0.0375498,
    			0.0375498, 0.0375498, 0.0375498, 0.0375498, 0.0377898, 0.0377898,
    			0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898,
    			0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898,
    			0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898,
    			0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898,
    			0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898,
    			0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898,
    			0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0377898, 0.0378247,
    			0.0378247, 0.0378247, 0.0378247, 0.0378247, 0.0378247, 0.0379641,
    			0.0379641, 0.0379641, 0.0379641, 0.0379641, 0.0379641, 0.0379641,
    			0.0379641, 0.0379641, 0.0379641, 0.0379641, 0.0379641, 0.0379641,
    			0.0379641, 0.0379641, 0.0379641, 0.0379641, 0.0379641, 0.0379641,
    			0.0379641, 0.0379641, 0.0379641, 0.0379641, 0.0379641, 0.0379641,
    			0.0379641, 0.0379641, 0.0379641, 0.0379641, 0.0379641, 0.0379641,
    			0.0380341, 0.0380341, 0.0380341, 0.0380341, 0.0380341, 0.0380341,
    			0.0380341, 0.0380341, 0.0380341, 0.0380341, 0.0380341, 0.0380341,
    			0.0380341, 0.0380341, 0.0380341, 0.0380341, 0.0380341, 0.0380341,
    			0.0380341, 0.0380341, 0.0380341, 0.0380341, 0.0380341, 0.0380341,
    			0.0380341, 0.0380341, 0.0380341, 0.0380341, 0.0380341, 0.0380341,
    			0.0381042, 0.0381042, 0.0381042, 0.0381042, 0.0381042, 0.0381042,
    			0.0381042, 0.0381042, 0.0381042, 0.0381042, 0.0381042, 0.0381042,
    			0.0381042, 0.0381042, 0.0381042, 0.0381042, 0.0381042, 0.0381042,
    			0.0381042, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441, 0.0382441,
    			0.0382441, 0.0382441 
    	};
    	double s = avgSnSignalPerDom[nsnSigBin/10]/10.;
//	if (logger.isDebugEnabled()) logger.debug("SN signal[" + nsnSigBin + "]: " + s);
	return s;
    }

	public double[] getEffVolumeScaling() {
		return effVolumeScaling;
	}
	
	public void setEffVolumeScaling(double[] effVolumeScaling) {
		this.effVolumeScaling = effVolumeScaling;
		
	}

	public double[] getAvgSnSignal() {
		return avgSnSignal;
	}

	public void setAvgSnSignal(double[] avgSnSignal) {
		this.avgSnSignal = avgSnSignal;
	}

}

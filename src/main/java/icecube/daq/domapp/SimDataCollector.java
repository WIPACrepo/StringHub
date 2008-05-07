package icecube.daq.domapp;

import cern.jet.random.Poisson;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.bindery.StreamBinder;
import icecube.daq.dor.DOMChannelInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;

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
    private long            lastSupernova;        // last time a SN record was generated
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

    private static final Logger logger = Logger.getLogger(SimDataCollector.class);

    public SimDataCollector(int card, int pair, char dom, double[] avgSnSignal,
			double[] effVolumeScaling) {
		super(card, pair, dom);
		this.avgSnSignal = avgSnSignal;
		this.effVolumeScaling = effVolumeScaling;
	}

	public SimDataCollector(DOMChannelInfo chanInfo, BufferConsumer hitsConsumer)
    {
        this(chanInfo, null, hitsConsumer, null, null, null);
    }

    public SimDataCollector(DOMChannelInfo chanInfo,
                            DOMConfiguration config,
                            BufferConsumer hitsConsumer,
                            BufferConsumer moniConsumer,
                            BufferConsumer scalConsumer,
                            BufferConsumer tcalConsumer
                            )
    {
        super(chanInfo.card, chanInfo.pair, chanInfo.dom);
        this.mbid = chanInfo.mbid;
        this.numericMBID  = Long.parseLong(this.mbid, 16);
        this.hitsConsumer = hitsConsumer;
        this.moniConsumer = moniConsumer;
        this.scalConsumer = scalConsumer;
        this.tcalConsumer = tcalConsumer;
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
        logger.info("Shutting down data collector [" + card + "" + pair + "" + dom + "]");
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
            if (scalConsumer != null) scalConsumer.consume(otrava.asReadOnlyBuffer());
        } catch (IOException iox) {
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
        lastBeacon = 0L;

        try {
            // Simulate the device open latency
            Thread.sleep(1400);

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
                    lastSupernova = t;
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
                    generateSupernova(currTime);
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
        if (currTime - lastSupernova < 1000L) return 0;
        // Simulate SN wrap-around
        if (currTime - lastSupernova > 10000L) lastSupernova = currTime - 10000L;
        int dtms = (int) (currTime - lastSupernova);
        int nsn = dtms * 10000 / 16384;        
        // sn Data Challenge
		long runStartMilli = getRunStartTime()/10000000L + t0;
		long hundredSec = 100000L;
		long snStartTime = ((runStartMilli/hundredSec)+1)*hundredSec;	// start sn within the next 100 sec (in ms)
		int dtsnSig = (int) (lastSupernova - snStartTime);
		int nsnSig = dtsnSig*10000/16384;
		int maxnsnSig = 15000*10000/16384;
		double effVol = 1.;
		if (effVolumeEnabled) {
			int domZNum = 8*card + 2*pair + (2-(dom-'A'));	// dom = 'A' -> 2, dom = 'B' -> 1
			effVol = effVolumeScaling(domZNum);
		}
		//	
        lastSupernova = currTime;
        short recl = (short) (10 + nsn);
        ByteBuffer buf = ByteBuffer.allocate(recl+32);
        long utc = (currTime - t0) * 10000000L;
        long clk = utc / 250L;
        
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
        	if (snSigEnabled && (nsnSig+i+1>0) && (nsnSig+i<maxnsnSig)) {
//        		if (i==0) logger.debug("sn start time " + snStartTime);
    			snRate = snSignalPerDom(nsnSig + i)*effVol*(10./snDistance)*(10./snDistance);
    		}
           int scaler = poissonRandom.nextInt(300 * 0.0016384) + poissonRandom.nextInt(snRate);
           if (scaler > 15) scaler = 15;
           buf.put((byte) scaler);
        }
        buf.flip();
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
        logger.debug("Generated " + n + " events in interval " + lastGenHit + ":" + currTime);
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
            logger.debug("Writing " + buf.remaining() + " byte hit at UTC = " + utc);
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

    public String getMainboardId()
    {
        return mbid;
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
		double[] effVolumeScale = {
				0.86131816, 0.900976794, 0.952426901,
				0.988066749, 0.972684423, 0.917700341, 0.851039623, 0.802034156,
				0.824524517, 0.867265899, 0.903136506, 0.950265023, 0.991671685,
				1.01791703, 1.005385069, 0.953499986, 0.87933255, 0.838205941,
				0.855036742, 0.909225084, 0.965494593, 0.992703602, 0.992575763,
				0.96616737, 0.927323435, 0.923061431, 0.956556192, 1.003018974,
				1.025468167, 1.0123826, 0.964749229, 0.890575293, 0.790705284,
				0.678848252, 0.588118151, 0.544476483, 0.616595253, 0.83121491,
				0.969605466, 1.053574754, 1.077212952, 1.088104681, 1.0831634,
				1.094257178, 1.123387556, 1.16641495, 1.205811407, 1.232038876,
				1.238427549, 1.224732583, 1.202344601, 1.187851186, 1.196899549,
				1.241235662, 1.279426862, 1.302027186, 1.304013557, 1.293682476,
				1.27368659, 1.25035282
				};
		return effVolumeScale[domZNum-1];
	}

    /**
     * Contains supernova signal extracted from Livermore paper in 10*1.6384ms bins 
     * (we only have luminosity in 0.1sec resolution)
	 * @param nsnSigBin the 1.6384ms time bin to set
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
    	return avgSnSignalPerDom[nsnSigBin/10]/10.;
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

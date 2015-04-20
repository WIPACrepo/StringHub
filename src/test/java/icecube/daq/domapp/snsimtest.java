package icecube.daq.domapp;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.dor.DOMChannelInfo;

import org.apache.log4j.BasicConfigurator;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * Test for supernova signal simulation.  Takes one DOM, put supernova at 1 kpc and see if the
 * simulated signal is triggered and that the peak is big enough.
 *
 * @author maruyama
 *
 */
public class snsimtest implements BufferConsumer {

	ArrayList<Long> numericMBID = new ArrayList<Long>();
	ArrayList<Integer> snScaler = new ArrayList<Integer>();
	ArrayList<Long> snTime = new ArrayList<Long>();
//	ArrayList<Integer> snScalerBinned = new ArrayList<Integer>();
//	ArrayList<Integer> snTimeBinned = new ArrayList<Integer>();

	ArrayList<Long> numMBID = new ArrayList<Long>();
	ArrayList<Integer> oneSecScaler = new ArrayList<Integer>();

	ArrayList<Integer> integratedCounts = new ArrayList<Integer>();
	ArrayList<Double> signalTime = new ArrayList<Double>();
	ArrayList<Double> baseRate = new ArrayList<Double>();

	ArrayList<Long> t0 = new ArrayList<Long>();
	ArrayList<Long> startTime = new ArrayList<Long>();
	ArrayList<Long> previousUtc = new ArrayList<Long>();
	ArrayList<Short> previousRecl = new ArrayList<Short>();

	double[] effVolumeScaling = new double[60];
	double[] avgSnSignal = new double[916];

	int runLength = 900; // in seconds

	long[] snScalerBinned = new long[runLength];
	long[] snTimeBinned = new long[runLength];


	@BeforeClass
	public static void setupLogging()
	{
		BasicConfigurator.configure();
	}

	@Test

	public void testSnSignal() throws Exception {
		/*
		DOMChannelInfo chInfo = new DOMChannelInfo("123456789abc", 7, 0, 'B');
		DOMChannelInfo chInfo = new DOMChannelInfo("123456789abc", 4, 1, 'A');

		int domNum = 8*card + 2*pair + (2-(dom-'A'));
	 	dom# = 8*card + 2*pair + (2 for dom = 'A', 1 for dom = 'B') from top to bottom
	 	DOM = 57 (starting at 1) has the largest effective volume: card = 7, pair = 0, 'B'
	 	DOM = 36 has the smallest eff volume: card = 4, pair = 1, 'A'
		 *
		 */
		ArrayList<DOMChannelInfo> chInfo = new ArrayList<DOMChannelInfo>();

		String domName;
//		int i3string = 21;
		int position;

		for (int i3string = 0; i3string < 1; i3string++) {
			for (int i = 0; i < 60; i++) {
				position = i+1;
				domName = String.format("0000%02d%02d", i3string, position);
				int card = (position-1)/8;
				int pair = (position-1)%8/2;
				char dom = (char)('A'+position%2);
				chInfo.add(new DOMChannelInfo(domName, card, pair, dom));

				numMBID.add(Long.parseLong(chInfo.get(i).mbid, 16));
				t0.add(-1L);
				oneSecScaler.add(0);
				integratedCounts.add(0);
				signalTime.add(0D);
				baseRate.add(0D);
				startTime.add(-1L);
				previousRecl.add((short) 0);
				previousUtc.add(0L);
			}
		}

		DOMConfiguration config = new DOMConfiguration();
		config.setEffVolumeEnabled(true);
		config.setSnDistance(1.);
		config.setSnSigEnabled(true);

		ArrayList<SimDataCollector> s1 = new ArrayList<SimDataCollector>();

		for (int i = 0; i<chInfo.size(); i++) {
			s1.add( new SimDataCollector(
				chInfo.get(i),
				config,
				null, null, this, null,
				false
				)
				);
			s1.get(i).signalConfigure();
		}


		for (int i = 0; i<chInfo.size(); i++) {
			while (s1.get(i).getRunLevel() != RunLevel.CONFIGURED)
			{
				Thread.sleep(100);
			}
		}

		for (int i = 0; i<chInfo.size(); i++) {
			s1.get(i).signalStartRun();
		}
//		int runLength = 120;
		Thread.sleep(runLength*1000);

		for (int i = 0; i<chInfo.size(); i++) {
			s1.get(i).signalStopRun();
		}
		for (int i = 0; i<chInfo.size(); i++) {
			while (s1.get(i).getRunLevel() != RunLevel.CONFIGURED)
			{
				Thread.sleep(100);
			}
		}
		for (int i = 0; i<chInfo.size(); i++) {

			s1.get(i).signalShutdown();
		}

		try {
			FileWriter outFile = new FileWriter("/Users/rmaruyama/Documents/IceCubeDocs/SN/DataChallenge/binnedRates.txt", false);
			BufferedWriter out = new BufferedWriter(outFile);
			StringBuilder strBuffer = new StringBuilder();

			for (int i = 0; i<runLength ; i++) {
				strBuffer.append(snTimeBinned[i] + "\t" + snScalerBinned[i] + "\n");
			}
			strBuffer.append("\n");
			out.write(strBuffer.toString());
			out.flush();
			out.close();
	    	} catch (IOException e) {
	    		System.err.println ("Error writing to file");
	    }


//	/* section 1: use this portion only if section 1 in consumer is on */
//		try {
//	    	FileWriter outFile = new FileWriter("/Users/rmaruyama/Documents/IceCubeDocs/SN/DataChallenge/test.txt", false);
//	        BufferedWriter out = new BufferedWriter(outFile);
//	    	StringBuilder strBuffer = new StringBuilder();
//
//	    	for (int i = 0; i<snScaler.size() ; i++) {
//	    		strBuffer.append(numericMBID.get(i) + "\t" + snTime.get(i) + "\t" + snScaler.get(i) + "\n");
//	    	}
//	    	strBuffer.append("\n");
//	        out.write(strBuffer.toString());
//	        out.flush();
//	        out.close();
//		    } catch (IOException e) {
//		    	System.err.println ("Error writing to file");
//	    }
//
//		long oneSecond = 10000000000L;
//
//		for (int i = 0; i<snScaler.size(); ++i) {
//			// initialize if new MBID
//			if (! numMBID.contains(numericMBID.get(i))) {
//				numMBID.add(numericMBID.get(i));
//				t0.add(snTime.get(i));
//				oneSecScaler.add(0);
//				integratedCounts.add(0);
//				signalTime.add(0D);
//				baseRate.add(0D);
//			}
//
//			int mbidIndex = numMBID.indexOf(numericMBID.get(i));
//
//			if (snTime.get(i) - t0.get(mbidIndex) > oneSecond) {
//				// get baseRate if within first second
//				if ((snTime.get(i) - snTime.get(0)) / oneSecond == 1) {
//					baseRate.set(mbidIndex, (double) oneSecScaler.get(mbidIndex));
//				}
//				//  trigger signalTime if larger than 2*baseRate
//				if ((signalTime.get(mbidIndex)==0) && (oneSecScaler.get(mbidIndex) > 2*baseRate.get(mbidIndex))) {
//					signalTime.set(mbidIndex, (double) (snTime.get(i) - snTime.get(0))/oneSecond);
//				}
//				integratedCounts.set(mbidIndex, integratedCounts.get(mbidIndex)+oneSecScaler.get(mbidIndex));
//				oneSecScaler.set(mbidIndex, 0);
//				t0.set(mbidIndex, snTime.get(i));
//			}
//
//			// add scaler to oneSecScaler of the right MBID
//			oneSecScaler.set(mbidIndex, oneSecScaler.get(mbidIndex) + snScaler.get(i));
//		}
//	/* end section 1 */

		System.out.println("dom # \t baseRate \t cnts above bkg \t signalTime from run start ");

		for (int i = 0; i<numMBID.size(); ++i) {
			System.out.println((i+1) + "\t" + baseRate.get(i) + "\t" + (integratedCounts.get(i)-300*runLength) + "\t\t" + signalTime.get(i));
		}
		System.out.println("dom 57: " + (integratedCounts.get(57-1)-300*runLength) + " dom 36: " + (integratedCounts.get(36-1)-300*runLength) + " ratio = " + (float) (integratedCounts.get(57-1)-300*runLength)/(integratedCounts.get(36-1)-300*runLength));

		for (int i = 0; i < numMBID.size(); ++i) {
			assertEquals("missing DOMs", chInfo.size(), numMBID.size());
			assertEquals("Base rate is out of range in DOM " + numMBID.get(i), 300, baseRate.get(i), 30);
			assertTrue("Sn simulation did not trigger in DOM " + numMBID.get(i), signalTime.get(i) > 0);
			assertEquals("Trigger time out of synch", signalTime.get(0), signalTime.get(i), 1.1);
		}
		float ratio = (float) (integratedCounts.get(57-1)-300*runLength)/(integratedCounts.get(36-1)-300*runLength);
		assertEquals("Ratio of counts in signal is out of range", 1.304/0.544, ratio, 0.25);
	}



    public synchronized void  consume(ByteBuffer buf) throws IOException {
        if (buf.getInt(0) != 32) {              // if not the end of run poison symbol
            long mbid = buf.getLong(8);
            long utc = buf.getLong(24);
            short recl = buf.getShort(32);
            long timeBin = 16384000L;
            long oneSecond = 10000000000L;

			// initialize if start of run
            if (t0.get(0) == -1L) {
                for (int i = 0; i < numMBID.size(); ++i) {
    				t0.set(i, utc);
    				startTime.set(i, utc);
    				previousRecl.set(i, (short) (-1));
    				previousUtc.set(i, -1L);
    			}
            }
			int mbidIndex = numMBID.indexOf(mbid);
//			if (mbidIndex == 13) {
//				System.out.println(mbid + " " +(utc-(previousUtc.get(mbidIndex)+previousRecl.get(mbidIndex)*(timeBin))) + " " + (recl-10));
//			}
			if (previousUtc.get(mbidIndex)!= -1L) {	// if this is not the beginning :
				assertEquals("time stamp out of synch", utc, (previousUtc.get(mbidIndex)+previousRecl.get(mbidIndex)*(timeBin)));
				assertEquals("number of scalers not multiple of 4", 0,  (recl - 10)%4);
			}
			previousUtc.set(mbidIndex, utc);
			previousRecl.set(mbidIndex, (short) (recl-10));

//		/* section 1: use this to record every instance of snscaler. use only if section 1 in test is on */
//			for (int i = 0; i< recl-10; i++) {
//				int scaler = buf.get(i+42);
//				numericMBID.add(mbid);
//				snScaler.add(scaler);
//				snTime.add(utc + i*timeBin);
//			}
//		/* end Section 1 */

			for (int i = 0; i< recl-10; i++) {
                int scaler = buf.get(i+42);
                long scalerTime = utc + i*timeBin;
                long timeFromStart = scalerTime - startTime.get(0);

                /* Checks on trigger, baseRate, counts above background. */
                if (scalerTime - t0.get(mbidIndex) > oneSecond) {
    				if (signalTime.get(mbidIndex)==0) {
    	   				if (oneSecScaler.get(mbidIndex) > 2*300) {
    	   					signalTime.set(mbidIndex, (double) (scalerTime - startTime.get(mbidIndex))/oneSecond);
    	   					baseRate.set(mbidIndex, (double) baseRate.get(mbidIndex)/(signalTime.get(mbidIndex)-1));
    	   				} else {
    	   					baseRate.set(mbidIndex,  baseRate.get(mbidIndex)+oneSecScaler.get(mbidIndex));
    	   				}
    				}
    				oneSecScaler.set(mbidIndex, 0);
    				t0.set(mbidIndex, scalerTime);
    			}
    			oneSecScaler.set(mbidIndex, oneSecScaler.get(mbidIndex) + scaler);
				integratedCounts.set(mbidIndex, integratedCounts.get(mbidIndex) + scaler);

				/* Bin scaler into one-second time bins */
				snTimeBinned[(int) (timeFromStart/oneSecond)] = (int) (timeFromStart/oneSecond);
				snScalerBinned[(int) (timeFromStart/oneSecond)] = snScalerBinned[(int) (timeFromStart/oneSecond)] + scaler;
           }
			if (mbidIndex == 13) {
				System.out.println("time: " + t0.get(13));
			}
       }
    }

}

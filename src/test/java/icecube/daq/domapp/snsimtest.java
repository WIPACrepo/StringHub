package icecube.daq.domapp;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Vector;

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

	Vector<Long> numericMBID = new Vector<Long>();
	Vector<Integer> snScaler = new Vector<Integer>();
	Vector<Long> snTime = new Vector<Long>();
	Vector<Integer> snScalerBinned = new Vector<Integer>();
	Vector<Long> snTimeBinned = new Vector<Long>();
		
//	Vector<DOMChannelInfo> chInfo = new Vector<DOMChannelInfo>();

	Vector<Long> numMBID = new Vector<Long>();
	Vector<Integer> oneSecScaler = new Vector<Integer>();
	
	Vector<Integer> integratedCounts = new Vector<Integer>();
	Vector<Double> signalTime = new Vector<Double>();
	Vector<Integer> baseRate = new Vector<Integer>();
	
	Vector<Long> t0 = new Vector<Long>();
	Vector<Long> startTime = new Vector<Long>();	
	
	double[] effVolumeScaling = new double[60];
	double[] avgSnSignal = new double[916];
	
	
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
		Vector<DOMChannelInfo> chInfo = new Vector<DOMChannelInfo>();
		
		String domName;
		int i3string = 21;
		int position;

		for (int i = 0; i < 60; i++) {
			position = i;
			domName = String.format("0000%02d%02d", i3string, position);
			int card = i/8;
			int pair = i%8/2;
			char dom = (char)('A'+i%2);
			chInfo.add(new DOMChannelInfo(domName, card, pair, dom));	

			numMBID.add(Long.parseLong(chInfo.get(i).mbid, 16));
			t0.add(-1L);
			oneSecScaler.add(0);
			integratedCounts.add(0);
			signalTime.add(0D);
			baseRate.add(0);
			startTime.add(-1L);
		}
		
		
		DOMConfiguration config = new DOMConfiguration();
		config.setEffVolumeEnabled(true);
		config.setSnDistance(1.);
		config.setSnSigEnabled(true);
		
		Vector<SimDataCollector> s1 = new Vector<SimDataCollector>();
		
		for (int i = 0; i<chInfo.size(); i++) {		
			s1.add( new SimDataCollector(
				chInfo.get(i), 
				config,
				null, null, this, null
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
		int runLength = 120;
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

//		try {
//	    	FileWriter outFile = new FileWriter("/Users/rmaruyama/Desktop/test.txt", false);
//	        BufferedWriter out = new BufferedWriter(outFile);
//	    	StringBuffer strBuffer = new StringBuffer();
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
//				baseRate.add(0);
//			}
//			
//			int mbidIndex = numMBID.indexOf(numericMBID.get(i));
//		
//			if (snTime.get(i) - t0.get(mbidIndex) > oneSecond) {		
//				// get baseRate if within first second
//				if ((snTime.get(i) - snTime.get(0)) / oneSecond == 1) {
//					baseRate.set(mbidIndex, oneSecScaler.get(mbidIndex));
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
		
		for (int i = 0; i<numMBID.size(); ++i) {
			System.out.println(i + "\t" + numMBID.get(i) + "\t" + (integratedCounts.get(i)-300*runLength) + "\t" + baseRate.get(i));
			
//		    System.out.println("mbid = " + chInfo.get(i).mbid + " numMBID = " + numMBID.get(i) + " parsed = " + Long.parseLong(chInfo.get(i).mbid, 16));
		}
		
		for (int i = 0; i < numMBID.size(); ++i) {
			System.out.println("dom #: " + i);
			System.out.println("Average rate for the first second = "+ baseRate.get(i) + " Hz");
			System.out.println("Total counts above background = "+ (integratedCounts.get(i)-300*runLength));
			System.out.println("Time of trigger after simulation start time = " + signalTime.get(i) + " sec");
		
			assertEquals("missing DOMs", chInfo.size(), numMBID.size());
			assertEquals("Base sn rate is out of range in DOM " + numMBID.get(i), 300, baseRate.get(i), 50);
			assertTrue("Sn simulation did not trigger in DOM " + numMBID.get(i), signalTime.get(i) > 0);			
			assertEquals("Trigger time out of synch", signalTime.get(0), signalTime.get(i), 0.1);
		}
		float ratio = (float) (integratedCounts.get(numMBID.indexOf(8534L))-300*runLength)/(integratedCounts.get(numMBID.indexOf(8501L))-300*runLength);
//		if (ratio < 1) {
//			ratio = 1/ratio;
//		}
		assertEquals("Ratio of counts in signal is out of range", 1.304/0.544, ratio, 0.3);
	}
	
//	public synchronized void  consume(ByteBuffer buf) throws IOException {
//		if (buf.getInt(0) != 32) {		// if not the end of run poison symbol
//			long numMBID = buf.getLong(8);
//			long utc = buf.getLong(24);
//			short recl = buf.getShort(32);
//			long timeBin = 16384000L;
//
//  		for (int i = 0; i< recl-10; i++) {
//				int scaler = buf.get(i+42);
//				numericMBID.add(numMBID);
//				snScaler.add(scaler);
//				snTime.add(utc + i*timeBin);
//			}
//		}
//	}

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
    			}			           	
            }
			int mbidIndex = numMBID.indexOf(mbid);

			for (int i = 0; i< recl-10; i++) {
                int scaler = buf.get(i+42);
                long scalerTime = utc + i*timeBin;
 //               numericMBID.add(mbid);
 //               snScaler.add(scaler);
 //               snTime.add(scalerTime);
                
                if ((scalerTime - t0.get(mbidIndex)) > oneSecond) {		
    				// get baseRate if within first second
    				if ((scalerTime - startTime.get(mbidIndex)) / oneSecond == 1) {
    					baseRate.set(mbidIndex, oneSecScaler.get(mbidIndex));
    				}
    				// signalTime is set (triggered) if larger than 2*baseRate
    				if ((signalTime.get(mbidIndex)==0) && (oneSecScaler.get(mbidIndex) > 2*baseRate.get(mbidIndex))) {
    					signalTime.set(mbidIndex, (double) (scalerTime - startTime.get(mbidIndex))/oneSecond);
    				}
    				oneSecScaler.set(mbidIndex, 0);
    				t0.set(mbidIndex, scalerTime);
    				
 //   				numericMBID.clear();
 //   				snScaler.clear();
 //   				snTime.clear();
    			}
    			// add scaler to oneSecScaler of the right MBID
    			oneSecScaler.set(mbidIndex, oneSecScaler.get(mbidIndex) + scaler);
				integratedCounts.set(mbidIndex, integratedCounts.get(mbidIndex) + scaler);
           }
        }
    }

}

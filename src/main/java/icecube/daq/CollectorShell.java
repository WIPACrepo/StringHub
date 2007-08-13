package icecube.daq;

import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.domapp.BadEngineeringFormat;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.MuxState;
import icecube.daq.domapp.RunLevel;
import icecube.daq.domapp.SimDataCollector;
import icecube.daq.domapp.EngineeringRecordFormat;
import icecube.daq.domapp.TriggerMode;
import icecube.daq.domapp.LocalCoincidenceConfiguration.Type;
import icecube.daq.dor.DOMChannelInfo;

/**
 * A collector shell wraps a single DataCollector so that it may be 
 * driven from the command-line.  
 * @author krokodil
 *
 */
public class CollectorShell 
{
	private AbstractDataCollector collector;
	private DOMConfiguration config;
	
	CollectorShell()
	{
		config = new DOMConfiguration();
	}
	
	/**
	 * Parse the options sent to the collector shell.  Options are
	 * <dl>
	 * <dt>-engformat=(<i>NFADC</i>, <i>NATWD0[:12]</i>, ...)</dt>
	 * <dd>Set the engineering record format</dd>
	 * <dt>-delta</dt>
	 * <dd>Set delta compressed output</dd>
	 * <dt>-hv=<i>HV</i></dt>
	 * <dd>Set the PMT high voltage in ADC counts (0.5 V units)</dd>
	 * <dt>-spe=<i>SPE</i></dt>
	 * <dd>Set the SPE disc value</dd>
	 * <dt>-mpe=<i>MPE</i></dt>
	 * <dd>Set the MPE disc value</dd>
	 * <dt>-trigger=(<i>forced | spe | mpe | flasher</i>)</dt>
	 * <dd>Specify the DOM trigger source</dd>
	 * </dl>
	 * @param option
	 * @throws BadEngineeringFormat
	 */
	public void parseOption(String option) throws BadEngineeringFormat
	{
		if (option.startsWith("engformat="))
		{
			// Parse out a string like (nfadc, natwd0[:size], natwd1[:size], natwd2[:size], natwd3[:size])
			Pattern pat = Pattern.compile("\\((\\d+),(\\d+(?::[12])?),(\\d+(?::[12])?),(\\d+(?::[12])?),(\\d+(?::[12])?)\\)");
			Matcher mat = pat.matcher(option.substring(10));
			if (!mat.matches()) throw new IllegalArgumentException(option);
			short[] atwdSamp = new short[4];
			short[] atwdSize = new short[] { 2, 2, 2, 2 };
			short nfadc = Short.parseShort(mat.group(1));
			for (int i = 0; i < 4; i++)
			{
				String x = mat.group(i+2);
				int sep = x.indexOf(':');
				if (sep < 0) 
					sep = x.length();
				else
					atwdSize[i] = Short.parseShort(x.substring(sep+1, x.length()));
				atwdSamp[i] = Short.parseShort(x.substring(0, sep));
			}
			config.setEngineeringFormat(new EngineeringRecordFormat(nfadc, atwdSamp, atwdSize));
		}
		else if (option.equals("delta"))
		{
			config.enableDeltaCompression();
		}
		else if (option.startsWith("hv="))
		{
			config.setHV(Short.parseShort(option.substring(3)));
		}
		else if (option.startsWith("spe="))
		{
			config.setDAC(9, Short.parseShort(option.substring(4)));
		}
		else if (option.startsWith("mpe="))
		{
			config.setDAC(8, Short.parseShort(option.substring(4)));
		}
		else if (option.startsWith("trigger="))
		{
			String trig = option.substring(8);
			if (trig.equals("forced"))
				config.setTriggerMode(TriggerMode.FORCED);
			else if (trig.equals("spe"))
				config.setTriggerMode(TriggerMode.SPE);
			else if (trig.equals("mpe"))
				config.setTriggerMode(TriggerMode.MPE);
			else if (trig.equals("flasher"))
				config.setTriggerMode(TriggerMode.FB);
		}
		else if (option.startsWith("pulser-rate="))
		{
			config.setPulserRate(Short.parseShort(option.substring(12)));
		}
		else if (option.startsWith("mux="))
		{
		    String muxOpt = option.substring(4);
		    for (MuxState m : MuxState.values())
		    {
		        if (muxOpt.equalsIgnoreCase(m.toString()))
		        {
		            config.setMux(m);
		            break;
		        }
		    }
		}
		else if (option.equals("slc"))
		{
		    config.getLC().setType(Type.SOFT);
		}
		else if (option.equals("debug"))
		{
			Logger.getRootLogger().setLevel(Level.DEBUG);
		}
	}
		
	public static void main(String[] args) throws Exception
	{
		CollectorShell csh = new CollectorShell();
		BasicConfigurator.configure();
		Logger.getRootLogger().setLevel(Level.INFO);
		
		int iarg = 0;
		boolean simMode = false;
		
		while (iarg < args.length)
		{
			String arg = args[iarg];
			if (arg.charAt(0) != '-') break;
			if (arg.substring(1).equalsIgnoreCase("sim")) 
			    simMode = true;
			else
			    csh.parseOption(arg.substring(1));
			iarg++;
		}
			
		if (args.length - iarg < 3)
		{
			System.out.println("usage : java [vmopt] class [opt] <runlength> <cwd> <output-file>");
			System.exit(1);
		}
		
		long rlm = Long.parseLong(args[iarg++]) * 1000L;
		
		// this argument should be the 'cwd' DOM specification (e.g. '20b')
		String cwd = args[iarg++];
		int card = Integer.parseInt(cwd.substring(0,1));
		int pair = Integer.parseInt(cwd.substring(1,2));
		char dom = cwd.charAt(2);
		
		// next argument is output filename
		String outBase = args[iarg++];
		
		FileOutputStream hitsOut = new FileOutputStream(outBase + ".hits");
		FileChannel hitsChannel = hitsOut.getChannel();

		FileOutputStream moniOut = new FileOutputStream(outBase + ".moni");
        FileChannel moniChannel = moniOut.getChannel();
        
        FileOutputStream tcalOut = new FileOutputStream(outBase + ".tcal");
        FileChannel tcalChannel = tcalOut.getChannel();
        
        FileOutputStream snOut = new FileOutputStream(outBase + ".sn");
        FileChannel snChannel = snOut.getChannel();
		
        if (simMode)
        {
            String mbid = "0123456789ab";
            csh.collector = new SimDataCollector(new DOMChannelInfo(mbid, card, pair, dom), csh.config,
                    hitsChannel, moniChannel, tcalChannel, snChannel);
        }
        else
        {
    		csh.collector = new DataCollector(card, pair, dom, csh.config, 
    		        hitsChannel, moniChannel, tcalChannel, snChannel);
        }
        
		csh.collector.start();
		
		csh.collector.signalConfigure();
		while (!csh.collector.getRunLevel().equals(RunLevel.CONFIGURED)) Thread.sleep(100);
		csh.collector.signalStartRun();
		Thread.sleep(rlm);
		csh.collector.signalStopRun();
		while (!csh.collector.getRunLevel().equals(RunLevel.CONFIGURED)) Thread.sleep(100);
		csh.collector.signalShutdown();
		csh.collector.join();
			
	}
}


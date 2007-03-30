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
import icecube.daq.domapp.SimDataCollector;
import icecube.daq.domapp.EngineeringRecordFormat;
import icecube.daq.domapp.TriggerMode;
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
	
	private void parseOption(String option) throws BadEngineeringFormat
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
		else if (option.equals("debug"))
		{
			Logger.getRootLogger().setLevel(Level.DEBUG);
		}
	}
		
	public static void main(String[] args) throws Exception
	{
		CollectorShell csh = new CollectorShell();
		BasicConfigurator.configure();
		Logger.getRootLogger().setLevel(Level.DEBUG);
		int iarg = 0;
		while (iarg < args.length)
		{
			String arg = args[iarg];
			if (arg.charAt(0) != '-') break;
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
		FileOutputStream output = new FileOutputStream(args[iarg++]);
		FileChannel ch = output.getChannel();
		
		String mbid = "0123456789ab";
		
		csh.collector = new SimDataCollector(
				new DOMChannelInfo(mbid, card, pair, dom), ch
				);
		csh.collector.setConfig(csh.config);
		csh.collector.start();
		
		// move the collector through its states
		// while (csh.collector.queryDaqRunLevel() == 0) Thread.sleep(100);
		csh.collector.signalConfigure();
		while (csh.collector.queryDaqRunLevel() != 2) Thread.sleep(100);
		csh.collector.signalStartRun();
		Thread.sleep(rlm);
		csh.collector.signalStopRun();
		while (csh.collector.queryDaqRunLevel() != 2) Thread.sleep(100);
		csh.collector.signalShutdown();
		csh.collector.join();
			
	}
}


package icecube.daq;

import icecube.daq.bindery.BufferConsumerChannel;
import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.domapp.BadEngineeringFormat;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.EngineeringRecordFormat;
import icecube.daq.domapp.LocalCoincidenceConfiguration.RxMode;
import icecube.daq.domapp.LocalCoincidenceConfiguration.Type;
import icecube.daq.domapp.MuxState;
import icecube.daq.domapp.RunLevel;
import icecube.daq.domapp.SimDataCollector;
import icecube.daq.domapp.TriggerMode;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.util.FlasherboardConfiguration;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

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
    private FlasherboardConfiguration flasherConfig;
    private static final Logger logger = Logger.getLogger(CollectorShell.class);

    public CollectorShell()
    {
        this(null);
    }

    public CollectorShell(Properties props)
    {
        config = new DOMConfiguration();
        flasherConfig = null;

	if (props == null) return;

	if (props.containsKey("icecube.daq.collectorshell.lc.type")) {
	    String lcType = props.getProperty("icecube.daq.collectorshell.lc.type");
	    if (lcType.equalsIgnoreCase("hard")) {
	        config.getLC().setType(Type.HARD);
            }
	    else if (lcType.equalsIgnoreCase("soft")) {
	        config.getLC().setType(Type.SOFT);
            }
	}
	if (props.containsKey("icecube.daq.collectorshell.lc.rxmode"))
	{
	    String rxMode = props.getProperty("icecube.daq.collectorshell.lc.rxmode");
	    if (rxMode.equalsIgnoreCase("none")) {
	        config.getLC().setRxMode(RxMode.RXNONE);
            }
	    else if (rxMode.equalsIgnoreCase("up")) {
	        config.getLC().setRxMode(RxMode.RXUP);
            }
	    else if (rxMode.equalsIgnoreCase("down")) {
	        config.getLC().setRxMode(RxMode.RXDOWN);
            }
	    else if (rxMode.equalsIgnoreCase("both")) {
	        config.getLC().setRxMode(RxMode.RXBOTH);
            }
	}

	if (props.containsKey("icecube.daq.collectorshell.lc.span"))
	{
	    config.getLC().setSpan((byte) Integer.parseInt(
                props.getProperty("icecube.daq.collectorshell.lc.span")
                ));
	}

        if (props.containsKey("icecube.daq.collectorshell.lc.pretrig"))
        {
            config.getLC().setPreTrigger(Integer.parseInt(
                props.getProperty("icecube.daq.collectorshell.lc.pretrig")
                ));
        }

        if (props.containsKey("icecube.daq.collectorshell.lc.posttrig"))
        {
            config.getLC().setPostTrigger(Integer.parseInt(
                props.getProperty("icecube.daq.collectorshell.lc.posttrig")
                ));
        }

	}

	/**
	 * Parse the options sent to the collector shell.  Options are
	 * <dl>
	 * <dt>-engformat=(<i>NFADC</i>, <i>NATWD0[:12]</i>, ...)</dt>
	 * <dd>Set the engineering record format</dd>
	 * <dt>-delta</dt>
	 * <dd>Set delta compressed output</dd>
	 * <dt>-pedsub</dt>
	 * <dd>Subtract pedestals in the DOM</dd>
	 * <dt>-hv=<i>HV</i></dt>
	 * <dd>Set the PMT high voltage in ADC counts (0.5 V units)</dd>
	 * <dt>-spe=<i>SPE</i></dt>
	 * <dd>Set the SPE disc value</dd>
	 * <dt>-mpe=<i>MPE</i></dt>
	 * <dd>Set the MPE disc value</dd>
	 * <dt>-trigger=(<i>forced | spe | mpe | flasher</i>)</dt>
	 * <dd>Specify the DOM trigger source</dd>
	 * <dt>-flasher[:width=w,brightness=b,rate=r,delay=d,mask=hex]</dt>
	 * <dd>Start a flasherboard run with the given parameters</dd>
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
		else if (option.equals("pedsub"))
		{
		    config.setPedestalSubtraction(true);
		}
		else if (option.startsWith("flasher"))
		{
		    flasherConfig = new FlasherboardConfiguration();
		    if (option.length() > 8 && option.charAt(7) == ':')
		    {
		        Pattern p = Pattern.compile("(\\w+)=(\\w+)");
		        String[] flOpts = option.substring(8).split(",");
		        for (String flop : flOpts)
		        {
		            Matcher m = p.matcher(flop);
		            if (m.matches())
		            {
		                String arg = m.group(1);
		                String val = m.group(2);
		                if (arg.equalsIgnoreCase("brightness"))
		                {
		                    flasherConfig.setBrightness(Integer.parseInt(val));
		                }
		                else if (arg.equalsIgnoreCase("width"))
		                {
		                    flasherConfig.setWidth(Integer.parseInt(val));
		                }
		                else if (arg.equalsIgnoreCase("delay"))
		                {
		                    flasherConfig.setDelay(Integer.parseInt(val));
		                }
		                else if (arg.equalsIgnoreCase("rate"))
		                {
		                    flasherConfig.setRate(Integer.parseInt(val));
		                }
		                else if (arg.equalsIgnoreCase("mask"))
		                {
		                    flasherConfig.setMask(Integer.parseInt(val, 16));
		                }
		            }
		        }
		    }
		}
		else if (option.startsWith("dac"))
		{
		    int dac = Integer.parseInt(option.substring(3, 5));
		    int val = Integer.parseInt(option.substring(6));
		    config.setDAC(dac, val);
		}
		else if (option.startsWith("debug"))
		{
		    int c = option.indexOf(':');
		    if (c >= 0)
		    {
		        String classname = option.substring(c+1);
		        Logger.getLogger(classname).setLevel(Level.DEBUG);
		    }
		    else
		    {
		        Logger.getRootLogger().setLevel(Level.DEBUG);
		    }
		}
		else if (option.equals("info"))
		{
		    Logger.getRootLogger().setLevel(Level.INFO);
		}
	}

	public FlasherboardConfiguration getFlasherConfig()
	{
	    return flasherConfig;
	}

	public DOMConfiguration getConfig()
	{
	    return config;
	}

	public static void main(String[] args) throws Exception
	{
	    Properties props = new Properties();
        try
        {
            props.load(new FileInputStream("collectorshell.properties"));
            PropertyConfigurator.configure(props);
        }
        catch (IOException iox)
        {
            BasicConfigurator.configure();
        }

		CollectorShell csh = new CollectorShell(props);

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
		BufferConsumerChannel hitsConsumer = new BufferConsumerChannel(hitsOut.getChannel());

		FileOutputStream moniOut = new FileOutputStream(outBase + ".moni");
        BufferConsumerChannel moniConsumer = new BufferConsumerChannel(moniOut.getChannel());

        FileOutputStream tcalOut = new FileOutputStream(outBase + ".tcal");
        BufferConsumerChannel tcalConsumer = new BufferConsumerChannel(tcalOut.getChannel());

        FileOutputStream snOut = new FileOutputStream(outBase + ".sn");
        BufferConsumerChannel scalConsumer = new BufferConsumerChannel(snOut.getChannel());

        if (simMode)
        {
            String mbid = "00000000ABCD";
            csh.collector = new SimDataCollector(new DOMChannelInfo(mbid, card, pair, dom), csh.config,
                    hitsConsumer, moniConsumer, scalConsumer, tcalConsumer,
                    false);
        }
        else
        {
    		csh.collector = new DataCollector(card, pair, dom, csh.config,
    		        hitsConsumer, moniConsumer, scalConsumer, tcalConsumer,
    		        null, null);
        }

		csh.collector.signalConfigure();
		while (!csh.collector.getRunLevel().equals(RunLevel.CONFIGURED)) Thread.sleep(100);
		csh.collector.signalStartRun();
		if (csh.flasherConfig != null)
		{
		    Thread.sleep(2000L);
	        csh.collector.setFlasherConfig(csh.flasherConfig);
	        csh.collector.signalStartSubRun();
		}
		Thread.sleep(rlm);
		csh.collector.signalStopRun();
		while (!csh.collector.getRunLevel().equals(RunLevel.CONFIGURED)) Thread.sleep(100);
		logger.info("Shutting down");
		csh.collector.signalShutdown();
		logger.info("Exit.");
	}
}

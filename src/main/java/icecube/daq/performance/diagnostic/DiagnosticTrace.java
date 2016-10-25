package icecube.daq.performance.diagnostic;


import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import icecube.daq.performance.diagnostic.Content.*;
import icecube.daq.performance.diagnostic.Content.GCContent;
import icecube.daq.performance.diagnostic.Content.TimeContent;
import icecube.daq.performance.diagnostic.Content.HeapMemoryContent;

/**
 * Utility to dump diagnostic content as a running table of formatted lines.
 *
 * <PRE>
 * Usage:
 *
 *   DiagnosticTrace trace = new DiagnosticTrace(1000, 30);
 *   trace.addTimeContent();
 *   trace.addAgeContent();
 *   trace.addMeter("sort_in", sortQueue, MeterContent.Style.DATA_RATE_IN);
 *   trace.addMeter("sorter", sorter, MeterContent.Style.HELD_DATA,
 *                                    MeterContent.Style.UTC_DELAY,
 *                                    MeterContent.Style.DATA_RATE_OUT);
 *   trace.addMeter("spool", spool, MeterContent.Style.DATA_RATE_OUT);
 *   trace.start();
 *   //...
 *   trace.stop();
 *
 * Examples:
 *
 *
 * #time                     age   heapmb  ygcc   ygct   ogcc   ogct
 *  2016-09-21 16:11:02.990  1.0m  11      0      0      0      0
 *  2016-09-21 16:11:03.993  1.0m  11      0      0      0      0
 *  2016-09-21 16:11:04.998  1.0m  11      0      0      0      0
 *  2016-09-21 16:11:06.2    1.0m  11      0      0      0      0
 *  2016-09-21 16:11:07.6    1.1m  11      0      0      0      0
 *  2016-09-21 16:11:08.6    1.1m  11      0      0      0      0
 *  2016-09-21 16:11:09.10   1.1m  11      0      0      0      0
 *  2016-09-21 16:11:10.13   1.1m  11      0      0      0      0
 *  2016-09-21 16:11:11.14   1.1m  11      0      0      0      0
 *
 *
 * #sorter  [ msgq        mbq         delms       mbout       mbpsout     ]
 *          [ 979         73          2319        1798        32          ]
 *          [ 1054        78          3780        1828        24          ]
 *          [ 788         58          4299        1859        50          ]
 *          [ 1046        78          4946        1889        10          ]
 *          [ 1002        74          6303        1919        33          ]
 *          [ 485         36          1057        1949        69          ]
 *          [ 887         66          2064        1979        0           ]
 *          [ 501         37          1097        2009        58          ]
 *          [ 903         67          2106        2040        0           ]
 *
 *
 * #consumer  [ msgq        mbq         mbout       mbpsout     ]   spool  [ mbout       mbpsout     ]
 *            [ 860         64          1725        45          ]          [ 1660        45          ]
 *            [ 311         23          1749        65          ]          [ 1726        65          ]
 *            [ 978         73          1800        0           ]          [ 1726        0           ]
 *            [ 315         23          1810        60          ]          [ 1787        60          ]
 *            [ 760         57          1844        0           ]          [ 1787        0           ]
 *            [ 805         60          1913        65          ]          [ 1852        65          ]
 *            [ 805         60          1913        0           ]          [ 1852        0           ]
 *            [ 665         49          1972        69          ]          [ 1922        69          ]
 *            [ 665         49          1972        0           ]          [ 1922        0           ]
 *
 * </PRE>
 *
 * Content instances that need to share a per-line resource can utilize a
 * content fly-weight which provides a per-line hook method.
 */
public class DiagnosticTrace
{

    /* Top level content composing the full trace line. */
    private final ContentBuilder contentHolder = new ContentBuilder();

    /* Period of trace lines. */
    private final int period;

    /* Period (in lines) of header emit. */
    private final int headerPeriod;

    /* Timer firing the trace. */
    private Timer timer;

    /* Destination of trace lines. */
    private final PrintStream out;


    private static final int DEFAULT_PERIOD = 10000;
    private static final int DEFAULT_HEADER_PERIOD = Integer.MAX_VALUE;


    public DiagnosticTrace()
    {
        this(System.out);
    }

    public DiagnosticTrace(PrintStream out)
    {
        this(DEFAULT_PERIOD, out);
    }

    public DiagnosticTrace(int period)
    {
        this(period, DEFAULT_HEADER_PERIOD, System.out);
    }

    public DiagnosticTrace(int period, int headerPeriod)
    {
        this(period, headerPeriod, System.out);
    }

    public DiagnosticTrace(int period, PrintStream out)
    {
        this(period, DEFAULT_HEADER_PERIOD, out);
    }

    public DiagnosticTrace(int period, int headerPeriod, PrintStream out)
    {
        this.period = period;
        this.headerPeriod = headerPeriod;
        this.out = out;

        contentHolder.addContent(new CommentContent("#"));
    }

    public void addFlyWeight(FlyWeight flyWeight)
    {
        contentHolder.addFlyWeight(flyWeight);
    }

    public void addContent(Content content)
    {
        contentHolder.addContent(content);
    }

    public void addMeter(String name, Metered meter)
    {
        addMeter(name, meter, MeterContent.Style.HELD_DATA);
    }

    public void addMeter(String name, Metered meter,
                         MeterContent.Style... style)
    {
        addContent(new GroupedContent(name, new MeterContent(meter, style)));
    }

    public void addMeter(String name, Metered meter,
                         MeterContent.MeterField... fields)
    {
        addContent(new GroupedContent(name, new MeterContent(meter, fields)));
    }


    public void start()
    {
        synchronized (this)
        {
            if (timer != null)
            {
                throw new Error("Already started");
            }

            TimerTask task = new TimerTask()
            {
                final StringBuilder sb = new StringBuilder(1024);
                int lineCount;
                @Override
                public void run()
                {
                    try
                    {
                        if(lineCount++ % headerPeriod == 0)
                        {
                            sb.delete(0, sb.length());
                            contentHolder.header(sb);
                            out.println(sb.toString());
                            lineCount=1;
                        }

                        sb.delete(0, sb.length());

                        contentHolder.beforeContent();
                        contentHolder.content(sb);

                        out.println(sb.toString());
                    }
                    catch (Throwable th)
                    {
                        out.print("Stopping trace due to error:");
                        th.printStackTrace(out);
                        DiagnosticTrace.this.stop();
                    }
                }
            };

            timer = new Timer();
            timer.schedule(task, 0, period);

        }

    }

    public void stop()
    {
        timer.cancel();
    }

    public void addTimeContent()
    {
        addContent(new TimeContent());
    }

    public void addAgeContent()
    {
        addContent(new AgeContent());
    }

    public void addHeapContent()
    {
        addContent(new HeapMemoryContent());
    }

    public void addGCContent()
    {
        addContent(new GCContent());
    }

    public void addDividerContent()
    {
        addContent(new DividerContent());
    }


    /**
     * Content Holder.
     */
    private static class ContentBuilder implements Content, FlyWeight
    {
        private List<FlyWeight> flyWeights = new ArrayList<>(4);
        private List<Content> contents = new ArrayList<>(4);

        private void addFlyWeight(FlyWeight flyWeight)
        {
            flyWeights.add(flyWeight);
        }

        private void addContent(Content content)
        {
            contents.add(content);
        }

        @Override
        public void beforeContent()
        {
            for (FlyWeight flyWeight : flyWeights)
            {
                flyWeight.beforeContent();
            }
        }

        @Override
        public void header(final StringBuilder sb)
        {
            for (Content content : contents)
            {
                content.header(sb);
            }
        }

        @Override
        public void content(final StringBuilder sb)
        {
            for (Content content : contents)
            {
                content.content(sb);
            }
        }

    }


}

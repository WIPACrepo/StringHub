package icecube.daq.performance.diagnostic.cpu;

import icecube.daq.performance.diagnostic.Content;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides cpu utilization trace content.
 *
 * Instantiation of content is though factory methods on an instance so
 * that sample readings can be shared via a flyweight instance.
 */
public class CPUUtilizationContent implements Content.FlyWeight
{

    private final int pid;
    private final CPUMonitor monitor;
    private final int iterationsPerSample;
    private int iteration;
    private CPUMonitor.Utilization latestSample;


    public CPUUtilizationContent()
    {
        this(1);
    }

    public CPUUtilizationContent(int iterationsPerSample)
    {
        this.pid = ThreadUtil.myPID();
        monitor = CPUMonitor.createFailsafe(pid);
        latestSample = monitor.sample();
        this.iterationsPerSample = iterationsPerSample;
    }

    int count;
    @Override
    public void beforeContent()
    {
        if(++iteration >= iterationsPerSample)
        {
            latestSample = monitor.sample();
            iteration = 0;
        }
    }


    /**
     * @return A System cpu content fly-weighted to this monitor.
     */
    public Content createSystemUtilizationContent()
    {
        return new SystemUtilizationContent();
    }

    /**
     * @return A process cpu content fly-weighted to this monitor.
     */
    public Content createProcessUtilizationContent()
    {
        return new PIDUtilizationContent(pid);
    }

    /**
     * @return A thread cpu content fly-weighted to this monitor.
     */
    public Content createThreadUtilizationContent(final int nid,
                                                  final String label)
    {
        monitor.addTid(nid);
        return new PIDUtilizationContent(nid, label);
    }

    /**
     * Note: Dependent on the threads existing at time of call.
     *
     * @return A aggregate cpu content for a group of threads
     * fly-weighted to this monitor.
     * @param pattern A regex to match threads in the group.
     * @throws Exception Likely an error accessing the native thread id.
     */
    public Content createThreadGroupUtilizationContent(final String pattern,
                                                  final String label)
            throws Exception
    {
        List<Integer> nids = new ArrayList<>();

        List<ThreadUtil.ThreadInfo> all =
                ThreadUtil.getThreadInfo(pid);

        for(ThreadUtil.ThreadInfo ti : all)
        {
            if(ti.name.matches(pattern))
            {
                if(ti.nid < Integer.MAX_VALUE)
                {
                    nids.add((int)(ti.nid));
                }
                else
                {
                    // unexpected
                    throw new IllegalArgumentException("Bad nid:" + ti.nid);
                }
            }
        }


        int[] primitive = new int[nids.size()];
        int idx=0;
        for (Integer nid : nids)
        {
            monitor.addTid(nid);
            primitive[idx] = nid.intValue();
            idx++;

        }
        return new PIDAggregateContent(primitive, label);
    }

    /**
     * provide access to the fly-weighted sample.
     */
    private float getCPUSample(CPUMonitor.Keys key)
    {
        Float val = latestSample.cpuUtilization.get(key);
        return (val != null) ? val.floatValue() : Float.NaN;
    }

    /**
     * provide access to the fly-weighted sample.
     */
    private float getProcessSample(int pid)
    {
        Float val = latestSample.byPID.get(pid);
        return (val != null) ? val.floatValue() : Float.NaN;
    }

    /**
     * provide access to the fly-weighted sample.
     */
    private float getThreadSample(int nid)
    {
        Float val = latestSample.byThread.get(nid);
        return (val != null) ? val.floatValue() : Float.NaN;
    }


    /**
     * Provides system cpu utilization.
     * Example
     * <PRE>
     *       cpu%
     *       37.3
     *       91.6
     *       38.3
     * </PRE>
     */
    private class SystemUtilizationContent implements Content
    {

        final String header;

        private SystemUtilizationContent()
        {

            header = String.format("%-8s", "cpu%");
        }

        @Override
        public void header(final StringBuilder sb)
        {
            sb.append(header);
        }

        @Override
        public void content(final StringBuilder sb)
        {
            float cpuVal =
                    CPUUtilizationContent.this.getCPUSample(
                            CPUMonitor.Keys.CPU_UTILIZATION);
            sb.append(String.format("%-8.1f", cpuVal));
        }
    }

    /**
     * Provides system cpu utilization.
     * Example
     * <PRE>
     *       user%  sys%  idle%  cpu%
     *       29.4   7.9   61.7   37.3
     *       56.1   35.5  8.4    91.6
     *       28.5   9.8   71.7   38.3
     * </PRE>
     */
    private class FullSystemUtilizationContent implements Content
    {

        final String header;

        private FullSystemUtilizationContent()
        {

            header = String.format("%-8s %-8s %-8s %-8s",
                    "user%", "sys%", "idle%", "cpu%");
        }

        @Override
        public void header(final StringBuilder sb)
        {
            sb.append(header);
        }

        @Override
        public void content(final StringBuilder sb)
        {
            float user =
                    CPUUtilizationContent.this.getCPUSample(
                            CPUMonitor.Keys.CPU_USER);
            float system =
                    CPUUtilizationContent.this.getCPUSample(
                            CPUMonitor.Keys.CPU_SYSTEM);
            float idle =
                    CPUUtilizationContent.this.getCPUSample(
                            CPUMonitor.Keys.CPU_IDLE);
            float cpuVal =
                    CPUUtilizationContent.this.getCPUSample(
                            CPUMonitor.Keys.CPU_UTILIZATION);

            sb.append(String.format("%-8.1f %-8.1f %-8.1f %-8.1f",
                    user, system, idle, cpuVal));
        }

    }


    /**
     * Provides process CPU usage.
     *
     * Example
     * <PRE>
     *       32541%
     *       37.3
     *       91.6
     *       38.3
     * </PRE>
     */
    private class PIDUtilizationContent implements Content
    {
        private final int pid;
        private final String label;

        final String header;
        final String contentFmt;

        private PIDUtilizationContent(final int pid)
        {
            this(pid, Integer.toString(pid));
        }
        private PIDUtilizationContent(final int pid,
                                      final String label)
        {
            this.pid = pid;
            this.label = label;

            int width = Math.max(4, label.length())  + 4;
            header = String.format("%-"+width+"s", label);
            contentFmt = "%-" + width + ".1f";
        }

        @Override
        public void header(final StringBuilder sb)
        {
            sb.append(header);
        }

        @Override
        public void content(final StringBuilder sb)
        {
            float pidVal = CPUUtilizationContent.this.getProcessSample(pid);
            sb.append(String.format(contentFmt, pidVal));
        }

    }

    /**
     * Provides thread CPU usage.
     *
     * Example
     * <PRE>
     *       32541%
     *       37.3
     *       91.6
     *       38.3
     * </PRE>
     */
    private class NIDUtilizationContent implements Content
    {
        private final int nid;
        private final String label;

        final String header;
        final String contentFmt;

        private NIDUtilizationContent(final int nid)
        {
            this(pid, Integer.toString(nid));
        }
        private NIDUtilizationContent(final int nid,
                                      final String label)
        {
            this.nid = nid;
            this.label = label;

            int width = Math.max(4, label.length())  + 4;
            header = String.format("%-"+width+"s", label);
            contentFmt = "%-" + width + ".1f";
        }

        @Override
        public void header(final StringBuilder sb)
        {
            sb.append(header);
        }

        @Override
        public void content(final StringBuilder sb)
        {
            float pidVal = CPUUtilizationContent.this.getThreadSample(nid);
            sb.append(String.format(contentFmt, pidVal));
        }

    }


    /**
     * Provides aggregation of thread CPU usage.
     *
     * Example
     * <PRE>
     *       collectors
     *       23.4
     *       22.1
     *       25.7
     * </PRE>
     */
    private class PIDAggregateContent implements Content
    {
        private final int[] pids;
        private final String label;

        final String header;
        final String contentFmt;

        private PIDAggregateContent(final int[] pids,
                                      final String label)
        {
            this.pids = pids;
            this.label = label;

            int width = Math.min(10, label.length())  + 4;
            header = String.format("%-"+width+"s", label);
            contentFmt = "%-" + width + ".1f";
        }

        @Override
        public void header(final StringBuilder sb)
        {
            sb.append(header);
        }

        @Override
        public void content(final StringBuilder sb)
        {
            float acc = 0.0f;
            for (int i = 0; i < pids.length; i++)
            {
                float val = CPUUtilizationContent.this.getThreadSample(pids[i]);
                if(!Float.isNaN(val))
                {
                    acc += val;
                }
            }
            sb.append(String.format(contentFmt, acc));
        }

    }


}

package icecube.daq.performance.diagnostic.cpu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides CPU utilization monitoring by way of periodic
 * procfs readings.
 *
 * Note: OS Dependent. Requires linux procfs files:
 * <PRE>
 *       /proc/stat               System cpu usage
 *       /proc/[pid]/stat         Process spu usage
 *       /proc/[pid]/[tid]/stat   Task/Thread cpu usage
 * </PRE>
 */
public interface CPUMonitor
{

    /**
     * Sample proc files and calculate utilization for the interval.
     * @return CPU utilization calculations in percent.
     */
    public Utilization sample();

    /**
     * Add a thread for independent cpu utilization tracking.
     *
     * @param tid The native thread id of a thread to track.
     */
    public void addTid(int tid);

    /**
     * Holds utilization statistics.
     */
    class Utilization
    {
        public final Map<Keys, Float> cpuUtilization;
        public final Map<Integer, Float> byPID;
        public final Map<Integer, Float> byThread;

        public Utilization(final Map<Keys, Float> cpuUtilization,
                           final Map<Integer, Float> byPID,
                           final Map<Integer, Float> byThread)
        {
            this.cpuUtilization = cpuUtilization;
            this.byPID = byPID;
            this.byThread = byThread;
        }
    }

    /**
     * Define special numeric keys for indexing non-pid stats.
     */
    enum Keys
    {
        CPU_UTILIZATION(-1),
        CPU_USER(-2),
        CPU_SYSTEM(-3),
        CPU_IDLE(-4);

        final int id;

        Keys(final int id)
        {
            this.id = id;
        }
    }

    /**
     * Create a monitor for a pid, falling back to a dummy monitor
     * if error occurs during instantiation.
     * @param pid The pid of the process to monitor.
     * @return The monitor.
     * @throws IOException Error accessing the required procfs files.
     */
    static CPUMonitor create(final int pid) throws IOException
    {
        return new ProcFileMonitor(pid);
    }

    /**
     * Create a monitor for a pid, falling back to a dummy monitor
     * if error occurs during instantiation.
     * @param pid The pid of the process to monitor.
     * @return The monitor.
     */
    static CPUMonitor createFailsafe(final int pid)
    {
        try
        {
            return new ProcFileMonitor(pid);
        }
        catch (IOException e)
        {
            return new CPUMonitor()
            {
                @Override
                public Utilization sample()
                {
                    return new Utilization(new HashMap<>(),
                            new HashMap<>(), new HashMap<>());
                }

                @Override
                public void addTid(final int tid)
                {
                }
            };
        }
    }


    /**
     * Implements CPUMonitor using procfs files to sample
     * CPU usage stats.
     *
     */
    public class ProcFileMonitor implements CPUMonitor
    {
        private final int pid;
        private final Map<Integer, Integer> tids;

        private Sample lastSample;

        private final Path SYSTEM_STAT_FILE = Paths.get("/proc/stat");
        private final Path PROCESS_PROC_DIR;
        private final Path PROCESS_TASK_DIR;
        private final Path PROCESS_STAT_FILE;

        /**
         * Holds readings from one iteration.
         */
        private static class Sample
        {
            private final Map<String, String[]> cpu;
            private final String[] process;
            private final Map<Integer, String[]> threads;

            private final long whenNano;


            public Sample(final Map<String, String[]> cpu,
                          final String[] process,
                          final Map<Integer, String[]> threads,
                          final long whenNano)
            {
                this.cpu = cpu;
                this.process = process;
                this.threads = threads;
                this.whenNano = whenNano;
            }
        }

        /**
         * Create a monitor for a process using procfs as a source for cpu stat
         * readings.
         * @param pid The process id.
         * @throws IOException
         */
        public ProcFileMonitor(final int pid) throws IOException
        {
            PROCESS_PROC_DIR = Paths.get("/proc", Integer.toString(pid));
            PROCESS_TASK_DIR = PROCESS_PROC_DIR.resolve("task");
            PROCESS_STAT_FILE = PROCESS_PROC_DIR.resolve("stat");

            if(!Files.exists(PROCESS_PROC_DIR))
            {
                throw new IllegalArgumentException("Proc file " +
                        PROCESS_PROC_DIR +  " not found");
            }

            this.pid = pid;
            tids = new HashMap<>();
            lastSample = sampleProcFiles();
        }

        /**
         * Add a thread/task to be monitored
         * @param tid The task id of a thread belonging to the process.
         */
        @Override
        public void addTid(final int tid)
        {
            if( ! tids.keySet().contains(tid))
            {
                tids.put(tid, 0);
            }
        }

        @Override
        public Utilization sample()
        {
            try
            {
                Sample current = sampleProcFiles();
                Utilization result = calculateUtilization(lastSample, current);
                lastSample = current;
                return result;
            }
            catch (IOException e)
            {
                return new Utilization(new HashMap<>(), new HashMap<>(),
                        new HashMap<>());
            }
        }

        private Utilization calculateUtilization(Sample last, Sample current)
        {

            // CPU Utilization
            Map<Keys, Float> cpuUtilization = new HashMap<>(4);

            long cpuTicks = calculateCPUTicks(current).totalTicks -
                    calculateCPUTicks(last).totalTicks;
            long userTicks = calculateCPUTicks(current).userTicks -
                    calculateCPUTicks(last).userTicks;
            long systemTicks = calculateCPUTicks(current).systemTicks -
                    calculateCPUTicks(last).systemTicks;
            long idleTicks = calculateCPUTicks(current).idleTicks -
                    calculateCPUTicks(last).idleTicks;

            cpuUtilization.put(Keys.CPU_UTILIZATION,
                    percent((userTicks+systemTicks), cpuTicks));
            cpuUtilization.put(Keys.CPU_USER,
                    percent(userTicks, cpuTicks));
            cpuUtilization.put(Keys.CPU_SYSTEM,
                    percent(systemTicks, cpuTicks));
            cpuUtilization.put(Keys.CPU_IDLE,
                    percent(idleTicks, cpuTicks));


            // Process percentage
            Map<Integer, Float> byProcess = new HashMap<>(1);

            long processTicks =
                    calculateProcessTicks(current) - calculateProcessTicks(last);

            // NOTE Calculating process utilization as the ratio of process
            // ticks to total ticks would appear correct, but it will not match
            // procps  (top, ps) which uses the ratio of process tick time to
            // wall clock time.
            // NOTE: Assuming a jiffie of 100 Hz.
            final float interval = (current.whenNano - last.whenNano) / 1000000000f;
            byProcess.put(pid, (1.0f/interval * processTicks));


            // Thread percentage, for threads active in both readings
            Map<Integer, Float> byThread = new HashMap<>(tids.size());
            for(Integer tid : tids.keySet())
            {
                String[] lastThreadSample = last.threads.get(tid);
                String[] currentThreadSample = current.threads.get(tid);
                if(lastThreadSample != null && currentThreadSample != null)
                {
                    long threadTicks =
                            calculateProcessOrTaskTicks(currentThreadSample) -
                                    calculateProcessOrTaskTicks(lastThreadSample);

                    byThread.put(tid, (1.0f/interval * threadTicks));
                }
            }

            return new Utilization(cpuUtilization, byProcess, byThread);

        }

        private static CPUStat calculateCPUTicks(Sample sample)
        {
            return calculateCPUTicks(sample.cpu.get("cpu"));
        }

        private static class CPUStat
        {
            final long userTicks;
            final long systemTicks;
            final long idleTicks;
            final long totalTicks;

            private CPUStat(final long userTicks, final long systemTicks, final long idleTicks)
            {
                this.userTicks = userTicks;
                this.systemTicks = systemTicks;
                this.idleTicks = idleTicks;
                this.totalTicks = userTicks+systemTicks+idleTicks;
            }
        }
        private static CPUStat calculateCPUTicks(String[] statLine)
        {
            long user = Long.parseLong(statLine[1]);   // user
            user += Long.parseLong(statLine[2]);       // nice
            long system = Long.parseLong(statLine[3]); // system
            long idle = Long.parseLong(statLine[4]);   // idle
            idle += Long.parseLong(statLine[5]);       // iowait
            system += Long.parseLong(statLine[6]);     // irq
            system += Long.parseLong(statLine[7]);     // softirq
            system += Long.parseLong(statLine[8]);     // steal

            // Note: For reference guest stats are already
            //       accounted for in user and nice.
            //long guest = Long.parseLong(statLine[9]);           //guest
            //long guestNice =  += Long.parseLong(statLine[10]);  //guest nice

            return new CPUStat(user, system, idle);
        }

        private static long calculateProcessTicks(Sample sample)
        {
            return calculateProcessOrTaskTicks(sample.process);
        }

        private static long calculateProcessOrTaskTicks(String[] statLine)
        {
            long totalTicks = Long.parseLong(statLine[13]);   // user
            totalTicks += Long.parseLong(statLine[14]);       // system

            return totalTicks;
        }


        /**
         * Read CPU, process and task stat files.
         * @return
         */
        private Sample sampleProcFiles() throws IOException
        {
            // NOTE: Read over under-samples process and task
            //       counters
            long whenNano = System.nanoTime();
            String[] process = samplePID();
            Map<Integer, String[]> threads = new HashMap<>(tids.size());
            List<Integer> toBeRemoved = new ArrayList<>();
            for (Integer tid : tids.keySet())
            {
                String[] value = sampleTID(tid.intValue());
                if(value != null)
                {
                    threads.put(tid, value);
                }
                else
                {
                    Integer errors = tids.get(tid);
                    if(errors < 5)
                    {
                        tids.put(tid, (errors+1));
                    }
                    else
                    {
                        //remove tracking for this thread
                        toBeRemoved.add(tid);
                    }
                }
            }
            for(Integer tid : toBeRemoved)
            {
                tids.remove(tid);
            }

            Map<String, String[]> cpu = sampleCPU();
            return new Sample(cpu, process, threads, whenNano);
        }






        public List<String> readCPUStat() throws IOException
        {
            List<String> lines = Files.readAllLines(SYSTEM_STAT_FILE);
            return lines;

        }

        public String readPIDStat() throws IOException
        {
            List<String> lines = Files.readAllLines(PROCESS_STAT_FILE);
            if(lines.size() == 1)
            {
                return lines.get(0);
            }
            else
            {
                return "";
            }
        }

        public String readTIDStat(final int tid) throws IOException
        {
            Path taskStatFile =
                    PROCESS_TASK_DIR.resolve(Integer.toString(tid) +
                            "/stat");
            List<String> lines = Files.readAllLines(taskStatFile);

            if(lines.size() == 1)
            {
                return lines.get(0);
            }
            else
            {
                return "";
            }
        }


        private Map<String, String[]> sampleCPU() throws IOException
        {
            // Example:
            // cpu  227323 1521 154255 30470737 148963 0 2178 0 0 0
            // cpu0 119813 792 80872 15283115 9729 0 1531 0 0 0
            // cpu1 107510 729 73382 15187622 139233 0 647 0 0 0
            // intr 39004661 242 2 0 0 0 0 0 0 1 0 0 0 4 0 0 0 0 0 431112 0 0 327 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 77669 2835950 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
            // ctxt 51673797
            // btime 1476129018
            // processes 398901
            // procs_running 2
            // procs_blocked 0
            // softirq 30395348 0 14307075 31086 2940184 474854 0 1 4259055 66314 8316779
            List<String> lines = readCPUStat();

            Map<String, String[]> catagories = new HashMap<>(16);
            for(String line : lines)
            {
                String[] fields = line.split("\\s+");
                catagories.put(fields[0], fields);
            }

            return catagories;
        }


        private String[] samplePID() throws IOException
        {
            // Example:
            // 573 (java) S 1 434 434 0 -1 4202496 28930 0 0 0 358 50 0 0 20 0 60 0 8998150 9774174208 86273 18446744073709551615 4194304 4196500 140730893387312 140730893369888 265879061245 0 0 2 16800973 18446744073709551615 0 0 17 18 0 0 0 0 0
            String line = readPIDStat();
            return line.split("\\s+");
        }

        private String[] sampleTID(int tid)
        {
            // Example:
            // 615 (java) S 1 434 434 0 -1 4202560 866 0 0 0 8 1 0 0 20 0 60 0 8998156 9774174208 86829 18446744073709551615 4194304 4196500 140730893387312 139848185632608 265879075422 0 4 2 16800973 18446744071579608298 0 0 -1 21 0 0 0 0 0

            try
            {
                String line = readTIDStat(tid);
                return line.split("\\s+");
            }
            catch (IOException e)
            {
                return null;
            }
        }


        private static float percent(long part, long total)
        {
            return ((float)part)/total * 100.0f;
        }

    }

}
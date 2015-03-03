package icecube.daq.replay;

import icecube.daq.sender.Sender;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;

public class FileHandler
{
    private static final Logger LOG =
        Logger.getLogger(FileHandler.class);

    /** ID of this replay hub */
    private int hubId;
    /** Type of files handled by this object */
    private DataStreamType dataType;

    /** Offset to apply to every hit time */
    private long timeOffset;
    /** Reader thread */
    private InputThread inThread;
    /** Processor thread */
    private DataThread dataThread;
    /** Writer thread */
    private OutputThread outThread;

    /**
     * Object which reads in, recalibrates and sends payloads
     *
     * @param hubId replay hub ID
     * @param dataType replay file type
     * @param fileReader source of payloads
     */
    FileHandler(int hubId, DataStreamType dataType,
                Iterator<ByteBuffer> fileReader)
    {
        this.hubId = hubId;
        this.dataType = dataType;

        // give the input thread a running start
        inThread = new InputThread(hubId, dataType, fileReader);
        inThread.start();
    }

    /**
     * Return the time when the first of the channels to stop has stopped.
     * @return the DAQ time (1E10 ticks/sec) of the hit which fulfills this
     *         condition.
     */
    public long getEarliestLastChannelHitTime()
    {
        if (dataThread == null) {
            LOG.error("No active hub#" + hubId +
                      " thread for getEarliestLastChannelHitTime");
            return 0L;
        }

        return dataThread.getEarliestLastChannelHitTime();
    }

    /**
     * Return the time when the last of the channels to report hits has
     * finally reported
     * @return the DAQ time (1E10 ticks/sec) of the hit which fulfills this
     *         condition
     */
    public long getLatestFirstChannelHitTime()
    {
        if (dataThread == null) {
            LOG.error("No active hub#" + hubId +
                      " thread for getLatestFirstChannelHitTime");
            return 0L;
        }

        return dataThread.getLatestFirstChannelHitTime();
    }

    /**
     * Return the number of payloads queued for reading.
     *
     * @return input queue size
     */
    public long getNumInputsQueued()
    {
        if (inThread == null) {
            return 0L;
        }

        return inThread.getNumQueued();
    }

    /**
     * Return the number of payloads queued for writing.
     *
     * @return output queue size
     */
    public long getNumOutputsQueued()
    {
        if (outThread == null) {
            return 0L;
        }

        return outThread.getNumQueued();
    }

    /**
     * Get the total time (in nanoseconds) behind the DAQ time.
     *
     * @return total nanoseconds behind the current DAQ time
     */
    public long getTotalBehind()
    {
        if (dataThread == null) {
            LOG.error("No active hub#" + hubId + " thread for getTotalBehind");
            return 0L;
        }

        return dataThread.getTotalBehind();
    }

    /**
     * Get the total number of payloads read.
     *
     * @return total payloads
     */
    public long getTotalPayloads()
    {
        if (dataThread == null) {
            LOG.error("No active hub#" + hubId +
                      " thread for getTotalPayloads");
            return 0L;
        }

        return dataThread.getTotalPayloads();
    }

    /**
     * Get the total time (in nanoseconds) spent sleeping in order to
     * match DAQ time to system time
     *
     * @return total nanoseconds spent sleeping
     */
    public long getTotalSleep()
    {
        if (dataThread == null) {
            LOG.error("No active hub#" + hubId + " thread for getTotalSleep");
            return 0L;
        }

        return dataThread.getTotalSleep();
    }

    /**
     * Set the offset applied to each payload being replayed.
     *
     * @param offset offset to apply to payload times
     */
    public void setReplayOffset(long offset)
    {
        timeOffset = offset;
    }

    public void startThreads(HandlerOutputProcessor out)
    {
        outThread = new OutputThread(hubId, dataType, out);
        outThread.start();

        dataThread = new DataThread(hubId, dataType, inThread,
                                           timeOffset, outThread);
        dataThread.start();
    }

    public void stopThreads()
    {
        dataThread.stopping();
    }

    public String toString()
    {
        return String.format("Hub#%d %s[%s|%s|%s]", hubId, dataType, inThread,
                             dataThread, outThread);
    }
}

/**
 * Class which reads payloads and pushes them into a queue.
 */
class InputThread
    implements Runnable
{
    private static final Logger LOG = Logger.getLogger(InputThread.class);

    private static final int MAX_QUEUED = 100000;

    /** ID of this replay hub */
    private int hubId;
    /** Type of files handled by this object */
    private DataStreamType dataType;

    /** Hit reader */
    private Iterator<ByteBuffer> rdr;
    /** Thread */
    private Thread thread;

    private boolean waiting;
    private boolean stopping;
    private boolean stopped;

    /** Input queue. */
    private Deque<ByteBuffer> inputQueue =
        new ArrayDeque<ByteBuffer>();

    InputThread(int hubId, DataStreamType dataType, Iterator<ByteBuffer> rdr)
    {
        this.hubId = hubId;
        this.dataType = dataType;
        this.rdr = rdr;

        thread = new Thread(this);
        thread.setName(dataType + "InputThread#" + hubId);

        stopped = true;
    }

    /**
     * Return the number of payloads queued for reading.
     *
     * @return input queue size
     */
    public long getNumQueued()
    {
        return inputQueue.size();
    }

    public boolean isStopped()
    {
        return stopped;
    }

    public boolean isWaiting()
    {
        return waiting;
    }

    public ByteBuffer next()
    {
        synchronized (inputQueue) {
            while (!stopping && !stopped) {
                if (inputQueue.size() != 0) {
                    break;
                }

                try {
                    inputQueue.wait();
                } catch (InterruptedException ie) {
                    // if we got interrupted, restart the loop and
                    //  we'll exit if we're stopping or out of data
                    continue;
                }
            }

            if (inputQueue.size() == 0) {
                return null;
            }

            ByteBuffer buf = inputQueue.removeFirst();
            inputQueue.notify();
            return buf;
        }
     }

    /**
     * Main input loop.
     */
    public void run()
    {
        stopped = false;

        while (!stopping && !stopped) {
            synchronized (inputQueue) {
                if (!stopping && inputQueue.size() >= MAX_QUEUED) {
                    try {
                        waiting = true;
                        inputQueue.wait();
                    } catch (InterruptedException ie) {
                        LOG.error("Interrupt while waiting for hub#" + hubId +
                                  " " + dataType + " input queue", ie);
                    }
                    waiting = false;
                }

                if (inputQueue.size() >= MAX_QUEUED) {
                    continue;
                }
            }

            ByteBuffer buf = rdr.next();
            if (buf == null) {
                break;
            }

            synchronized (inputQueue) {
                inputQueue.addLast(buf);
                inputQueue.notify();
            }
        }

        synchronized (inputQueue) {
            stopping = false;
            stopped = true;
        }
    }


    public void start()
    {
        thread.start();
    }

    public void stop()
    {
        synchronized (inputQueue) {
            stopping = true;
            inputQueue.notify();
        }
    }

    public String toString()
    {
        String sstr = (stopped ? ",stopped" : stopping ? ",stopping" : "");
        return "InputThread[hub#" + hubId + "," + dataType + ",in=" +
            inputQueue.size() + sstr + "]";
    }
}

/**
 * Payload file writer thread.
 */
class DataThread
    implements Runnable
{
    /** error logger */
    private static final Logger LOG =
        Logger.getLogger(DataThread.class);

    /** Nanoseconds per second */
    private static final long NS_PER_SEC = 1000000000L;

    /** ID of this replay hub */
    private int hubId;
    /** Type of files handled by this object */
    private DataStreamType dataType;
    /** hit reader thread */
    private InputThread inThread;
    /** Offset to apply to every hit time */
    private long timeOffset;
    /** hit writer thread */
    private OutputThread outThread;

    /** The actual thread object */
    private Thread realThread;
    /** 'true' if this thread has been started */
    private boolean started;
    /** 'true' if this thread is stopping */
    private boolean stopping;

    /** first and last times for every DOM */
    private HashMap<Long, DOMTimes> domTimes =
        new HashMap<Long, DOMTimes>();

    /** Total time spent sleeping so payload time matches system time */
    private long totalSleep;
    /** Total time spent behind the original stream */
    private long totalBehind;
    /** total number of payloads read */
    private long totPayloads;

    /**
     * Create payload rewriter thread.
     *
     * @param hubId replay hub ID
     * @param dataType replay file type
     * @param inThread thread which reads data from file(s)
     * @param timeOffset offset to use for recalibrating payload times
     * @param outThread thread which writes data to the sender
     */
    DataThread(int hubId, DataStreamType dataType, InputThread inThread,
               long timeOffset, OutputThread outThread)
    {
        this.hubId = hubId;
        this.dataType = dataType;
        this.inThread = inThread;
        this.timeOffset = timeOffset;
        this.outThread = outThread;

        realThread = new Thread(this);
        realThread.setName(dataType + "DataThread#" + hubId);
    }

    /**
     * No cleanup is needed.
     */
    private void finishThreadCleanup()
    {
    }

    /**
     * Return the time when the first of the channels to stop has stopped.
     * @return the DAQ time (1E10 ticks/sec) of the hit which fulfills this
     *         condition.
     */
    public long getEarliestLastChannelHitTime()
    {
        long earliestLast = Long.MAX_VALUE;
        boolean found = true;

        for (Long mbid : domTimes.keySet()) {
            long val = domTimes.get(mbid).getLastTime();
            if (val < 0L) {
                found = false;
                break;
            } else if (val < earliestLast) {
                earliestLast = val;
            }
        }

        if (!found) {
            return 0L;
        }

        return earliestLast;
    }

    /**
     * Return the time when the last of the channels to report hits has
     * finally reported
     * @return the DAQ time (1E10 ticks/sec) of the hit which fulfills this
     *         condition
     */
    public long getLatestFirstChannelHitTime()
    {
        long latestFirst = Long.MIN_VALUE;
        boolean found = true;

        for (Long mbid : domTimes.keySet()) {
            long val = domTimes.get(mbid).getFirstTime();
            if (val < 0L) {
                found = false;
                break;
            } else if (val > latestFirst) {
                latestFirst = val;
            }
        }

        if (!found || latestFirst < 0L) {
            return 0L;
        }

        return latestFirst;
    }

    /**
     * Get the total time (in nanoseconds) behind the DAQ time.
     *
     * @return total nanoseconds behind the current DAQ time
     */
    public long getTotalBehind()
    {
        return totalBehind;
    }

    /**
     * Get the total number of payloads read.
     *
     * @return total payloads
     */
    public long getTotalPayloads()
    {
        return totPayloads;
    }

    /**
     * Get the total time (in nanoseconds) spent sleeping in order to
     * match DAQ time to system time
     *
     * @return total nanoseconds spent sleeping
     */
    public long getTotalSleep()
    {
        return totalSleep;
    }

    private void process()
    {
        boolean firstPayload = true;
        TimeKeeper sysTime = new TimeKeeper(hubId, dataType, true);
        TimeKeeper daqTime = new TimeKeeper(hubId, dataType, false);

        int gapCount = 0;
        while (!stopping) {
            ByteBuffer buf = inThread.next();
            if (buf == null) {
                break;
            }

            totPayloads++;

            final long rawTime = BBUTC.get(buf);
            if (rawTime == Long.MIN_VALUE) {
                final String fmtStr =
                    "Ignoring hub#%d short %s buffer#%d (%d bytes)";
                LOG.error(String.format(fmtStr, hubId, dataType, totPayloads,
                                        buf.limit()));
                continue;
            }

            // set the DAQ time
            if (!daqTime.set(rawTime + timeOffset, totPayloads)) {
                // if the current time if before the previous time, skip it
                continue;
            }

            // update the raw buffer's hit time
            if (timeOffset != 0) {
                BBUTC.set(buf, daqTime.get());
            }

            // set system time
            if (!sysTime.set(System.nanoTime(), totPayloads)) {
                // if the current time if before the previous time, skip it
                continue;
            }

            long timeGap;
            if (firstPayload) {
                // don't need to recalibrate the first payload
                firstPayload = false;
                timeGap = 0;
            } else {
                // try to deliver payloads at the rate they were created

                // get the difference the current system time and
                //  the next payload time
                timeGap = daqTime.baseDiff() - sysTime.baseDiff();

                // whine if the time gap is greater than one second
                if (timeGap > NS_PER_SEC * 2) {
                    if (totPayloads < 10) {
                        // minimize gap for first few payloads
                        timeGap = NS_PER_SEC / 10L;
                    } else {
                        // complain about gap
                        final String fmtStr = "Huge time gap (%.2f sec) for" +
                            " hub#%d %s payload #%d";
                        final double dblGap =
                            ((double) timeGap / 10000000000.0);
                        LOG.error(String.format(fmtStr, dblGap, hubId,
                                                dataType, totPayloads));
                        if (++gapCount > 20) {
                            LOG.error("Too many huge gaps for hub#" + hubId +
                                      " " + dataType + " ... aborting");
                            break;
                        }
                    }

                    // reset base times
                    sysTime.setBase(timeGap);
                    daqTime.setBase(0L);
                }

                // if we're sending payloads too quickly, wait a bit
                if (timeGap > NS_PER_SEC) {
                    totalSleep += timeGap;

                    try {
                        final long ns_per_ms = 1000000L;
                        final long sleepMS = timeGap / ns_per_ms;
                        final int sleepNS = (int) (timeGap % ns_per_ms);
                        Thread.sleep(sleepMS, sleepNS);
                    } catch (InterruptedException ie) {
                        // ignore interrupts
                    }
                } else {
                    totalBehind -= timeGap;
                }
            }

            // record the DAQ time for this DOM
            long mbid = buf.getLong(8);
            if (!domTimes.containsKey(mbid)) {
                domTimes.put(mbid, new DOMTimes(mbid));
            }
            domTimes.get(mbid).add(daqTime.get());

            buf.flip();

            outThread.push(buf);

            if (timeGap >= 0) {
                // if we're ahead of the stream, don't overwhelm other threads
                Thread.yield();
            }
        }

        outThread.stop();
        inThread.stop();

        stopping = false;

        LOG.error("Finished queuing " + totPayloads + " " + dataType +
                  " on hub#" + hubId);
    }

private icecube.daq.payload.impl.PayloadFactory factory = new icecube.daq.payload.impl.PayloadFactory(null);
    /**
     * Main file writer loop.
     */
    public void run()
    {
        try {
            process();
        } catch (Throwable thr) {
            LOG.error("Processing failed on hub#" + hubId + " after " +
                      totPayloads + " " + dataType, thr);
        }

        finishThreadCleanup();

        started = false;
    }

    /**
     * Start the thread.
     */
    public void start()
    {
        if (started) {
            throw new Error("Thread has already been started!");
        }

        realThread.start();
        started = true;
    }

    /**
     * Notify the thread that it should stop
     */
    public void stopping()
    {
        if (!started) {
            throw new Error("Thread has not been started!");
        }

        stopping = true;
        realThread.interrupt();
    }

    public String toString()
    {
        String sstr = (stopping ? ",stopping" : started ? "" : ",stopped");
        return "DataThread[hub#" + hubId + "," + dataType + ",off=" +
            timeOffset + ",sleep=" + totalSleep + ",behind=" + totalBehind +
            ",tot=" + totPayloads + sstr + "]";
    }
}

/**
 * Class which writes payloads to an output channel.
 */
class OutputThread
    implements Runnable
{
    private static final Logger LOG = Logger.getLogger(OutputThread.class);

    /** ID of this replay hub */
    private int hubId;
    /** Type of files handled by this object */
    private DataStreamType dataType;
    /** Hit sender */
    private HandlerOutputProcessor out;

    private Thread thread;
    private boolean waiting;
    private boolean stopping;
    private boolean stopped;

    /** Output queue. */
    private Deque<ByteBuffer> outputQueue =
        new ArrayDeque<ByteBuffer>();

    /**
     * Create output thread.
     *
     * @param hubId replay hub ID
     * @param dataType replay file type
     * @param out output processor
     */
    public OutputThread(int hubId, DataStreamType dataType,
                        HandlerOutputProcessor out)
    {
        this.hubId = hubId;
        this.dataType = dataType;
        this.out = out;

        thread = new Thread(this);
        thread.setName(dataType + "OutputThread#" + hubId);

        stopped = true;
    }

    /**
     * Return the number of payloads queued for writing.
     *
     * @return output queue size
     */
    public long getNumQueued()
    {
        return outputQueue.size();
    }

    public boolean isStopped()
    {
        return stopped;
    }

    public boolean isWaiting()
    {
        return waiting;
    }

    public void notifyThread()
    {
        synchronized (outputQueue) {
            outputQueue.notify();
        }
    }

    public void push(ByteBuffer buf)
    {
        if (buf != null) {
            synchronized (outputQueue) {
                outputQueue.addLast(buf);
                outputQueue.notify();
            }
        }
    }

    /**
     * Main output loop.
     */
    public void run()
    {
        stopped = false;

        // sleep for a second so detector has a chance to get first good time
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            LOG.error("Initial hub#" + hubId + " " + dataType +
                      " output thread sleep interrupted", ie);
        }

        ByteBuffer buf;
        while (!stopping || outputQueue.size() > 0) {
            synchronized (outputQueue) {
                if (!stopping && outputQueue.size() == 0) {
                    try {
                        waiting = true;
                        outputQueue.wait();
                    } catch (InterruptedException ie) {
                        LOG.error("Interrupt while waiting for hub#" + hubId +
                                  " " + dataType + " output queue", ie);
                    }
                    waiting = false;
                }

                if (outputQueue.size() == 0) {
                    buf = null;
                } else {
                    buf = outputQueue.removeFirst();
                }
            }

            if (buf == null) {
                continue;
            }

            out.send(buf);
        }

        out.stop();

        stopping = false;
        stopped = true;

        LOG.error("Finished writing hub#" + hubId + " " + dataType + " data");
    }

    public void start()
    {
        thread.start();
    }

    public void stop()
    {
        synchronized (outputQueue) {
            stopping = true;
            outputQueue.notify();
        }
    }

    public String toString()
    {
        String sstr = (stopped ? ",stopped" : stopping ? ",stopping" : "");
        return "OutputThread[hub#" + hubId + "," + dataType + ",q=" +
            outputQueue.size() + ",out=" + out + sstr + "]";
    }
}

/**
 * Track various times.
 */
class TimeKeeper
{
    private static final Logger LOG = Logger.getLogger(TimeKeeper.class);

    private int hubId;
    private DataStreamType dataType;
    private boolean isSystemTime;
    private boolean initialized;
    private long firstTime;
    private long baseTime;
    private long lastTime;

    /**
     * Create a time keeper
     *
     * @param dataType - file type
     * @param isSystemTime - <tt>true</tt> if this is for the system time,
     *                       <tt>false</tt> for DAQ time
     */
    TimeKeeper(int hubId, DataStreamType dataType, boolean isSystemTime)
    {
        this.hubId = hubId;
        this.dataType = dataType;
        this.isSystemTime = isSystemTime;
    }

    /**
     * Return the difference between the last time and the base time (in ns).
     *
     * @return difference in nanoseconds
     */
    long baseDiff()
    {
        long diff = lastTime - baseTime;
        if (!isSystemTime) {
            // convert DAQ time (10ths of ns) to system time
            diff /= 10L;
        }
        return diff;
    }

    /**
     * Return the difference between the last time and the first time (in ns).
     *
     * @return difference in nanoseconds
     */
    long realDiff()
    {
        long diff = lastTime - firstTime;
        if (!isSystemTime) {
            // convert DAQ time (10ths of ns) to system time
            diff /= 10L;
        }
        return diff;
    }

    /**
     * Get the most recent time
     *
     * @return time (ns for system time, 10ths of ns for DAQ time)
     */
    long get()
    {
        return lastTime;
    }

    /**
     * Record the next time.
     *
     * @param time next time
     * @param hitNum sequential hit number to use in error reporting
     *
     * @return <tt>false</tt> if <tt>time</tt> preceeds the previous time
     */
    boolean set(long time, long hitNum)
    {
        if (!initialized) {
            firstTime = time;
            baseTime = time;
            initialized = true;
        } else if (time < lastTime) {
            String timeType;
            if (isSystemTime) {
                timeType = "System";
            } else {
                timeType = "DAQ";
            }

            final String fmtStr =
                "Hub#%d %s %s#%d went back %d in time! (cur %d, prev %d)";
            LOG.error(String.format(fmtStr, hubId, timeType, dataType, hitNum,
                                    lastTime - time, time, lastTime));
            return false;
        }

        lastTime = time;
        return true;
    }

    /**
     * Set the base time as <tt>offset</tt> from the most recent time
     *
     * @param offset time offset
     */
    void setBase(long offset)
    {
        baseTime = lastTime + offset;
    }
}

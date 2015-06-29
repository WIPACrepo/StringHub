package icecube.daq.time.monitoring;

import org.apache.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Receives clock measurements from various threads throughout
 * StringHub process and forwards them to the clock monitor on
 * a dedicated thread.
 */
class ClockReactor implements ClockProcessor
{

    private final Logger logger = Logger.getLogger(ClockReactor.class);

    /** The delegate target. */
    private final ClockProcessor delegate;

    /** Provides a dedicated, single threaded execution model. */
    private ScheduledExecutorService executor;

    /** Running status. */
    private volatile boolean running = false;


    /**
     * Create a reactor that wraps a delegate processor.
     *
     * @param delegate The processor that will receive data on the
     *                 reactor thread.
     */
    ClockReactor(final ClockProcessor delegate)
    {
        this.delegate = delegate;
    }

    /**
     * Activate the reactor. Pair with Shutdown.
     */
    void startup()
    {
        synchronized (this)
        {
            if(!running)
            {
                executor = Executors.newScheduledThreadPool(1,
                        new ReactorThreadFactory());
                running = true;
            }
        }
    }

    /**
     * Shutdown the reactor.
     */
    void shutdown()
    {
        synchronized(this)
        {
            if(running)
            {
                if(executor != null)
                {
                    executor.shutdownNow();
                    try
                    {
                        executor.awaitTermination(5, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException e)
                    {
                        logger.error("Could not shut down clock reactor" +
                                " thread");
                    }
                    finally
                    {
                        running = false;
                    }
                }
            }
        }
    }

    /**
     * Access to the running status.
     * @return
     */
    boolean isRunning()
    {
        synchronized (this)
        {
            return running;
        }
    }

    @Override
    public void process(ClockProcessor.GPSSnapshot gpssnap)
    {
        submit(new GPSSnapshotJob(gpssnap));
    }

    @Override
    public void process(ClockProcessor.TCALMeasurement tcal)
    {
        submit(new TCALJob(tcal));
    }

    @Override
    public void process(ClockProcessor.NTPMeasurement ntp)
    {
        submit(new NTPJob(ntp));
    }

    /**
     * Pass a job to run on the reactor thread.
     */
    private void submit(Runnable job)
    {
        synchronized (this)
        {
            if(running)
            {
                executor.submit(job);
            }
        }
    }


    /**
     * Enclosure for a GPS snapshot processing job.
     */
    private final class GPSSnapshotJob implements Runnable
    {
        final ClockProcessor.GPSSnapshot measurement;

        GPSSnapshotJob(final ClockProcessor.GPSSnapshot measurement)
        {
            this.measurement = measurement;
        }

        @Override
        public void run()
        {
            try
            {
                delegate.process(measurement);
            }
            catch (Throwable th)
            {
                logger.error("Error processing GPS snapshot");
            }
        }
    }


    /**
     * Enclosure for a TCAL processing job.
     */
    private final class TCALJob implements Runnable
    {
        final ClockProcessor.TCALMeasurement measurement;

        TCALJob(final ClockProcessor.TCALMeasurement measurement)
        {
            this.measurement = measurement;
        }

        @Override
        public void run()
        {
            try
            {
                delegate.process(measurement);
            }
            catch (Exception th)
            {
                logger.error("Error processing TCAL");
            }
        }
    }


    /**
     * Enclosure for an NTP processing job.
     */
    private final class NTPJob implements Runnable
    {
        final ClockProcessor.NTPMeasurement measurement;

        NTPJob(final ClockProcessor.NTPMeasurement measurement)
        {
            this.measurement = measurement;
        }

        @Override
        public void run()
        {
            try
            {
                delegate.process(measurement);
            }
            catch (Exception th)
            {
                logger.error("Error processing NTP");
            }
        }
    }


    /**
     * Factory for defining the reactor thread.
     */
    private static class ReactorThreadFactory implements ThreadFactory
    {

        @Override
        public Thread newThread(final Runnable runnable)
        {
            Thread thread = new Thread(runnable);
            thread.setName("Clock Monitor");
            return thread;
        }

    }


}

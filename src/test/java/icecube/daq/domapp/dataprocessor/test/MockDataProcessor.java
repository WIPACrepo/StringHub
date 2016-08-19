package icecube.daq.domapp.dataprocessor.test;

import icecube.daq.domapp.RunLevel;
import icecube.daq.domapp.dataprocessor.DataProcessor;
import icecube.daq.domapp.dataprocessor.DataProcessorError;
import icecube.daq.domapp.dataprocessor.DataStats;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.util.UTC;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
public class MockDataProcessor implements DataProcessor
{
    final Lock lock;

    final Object forever = new Object();

    public int processCount = 0;
    public boolean sawEOS = false;
    public boolean sawShutdown = false;

    public RunLevel runLevel;

    final DataStats stats = new DataStats(-1);

    public enum Mode
    {
        NORMAL,
        ONE_ERROR,               // generate errors for the next call
        ERROR,                   // generate errors for all calls
        BLOCK_ONCE_INTERRUPTABLY // take the next call into a wait
    }
    Mode mode = Mode.NORMAL;

    public int delayMillis;

    public MockDataProcessor()
    {
        lock = new ReentrantLock();
    }

    public void setMode(Mode mode)
    {
        this.mode = mode;
    }

    public void lock()
    {
        lock.lock();
    }

    public void unlock()
    {
        lock.unlock();
    }

    @Override
    public DataStats getDataCounters()
    {
        return stats;
    }

    @Override
    public void runLevel(final RunLevel runLevel) throws DataProcessorError
    {
        lock.lock();
        try
        {
            checkMode();
            this.runLevel = runLevel;
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void process(final StreamType stream, final ByteBuffer data)
            throws DataProcessorError
    {
        lock.lock();
        try
        {
            try{ Thread.sleep(delayMillis);} catch (InterruptedException e){}
            checkMode();
            processCount++;
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void eos(final StreamType stream) throws DataProcessorError
    {
        lock.lock();
        try
        {
            checkMode();
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void eos() throws DataProcessorError
    {
        lock.lock();
        try
        {
            checkMode();
            sawEOS = true;
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void shutdown(final long waitMillis) throws DataProcessorError
    {
        shutdown();
    }

    @Override
    public void shutdown() throws DataProcessorError
    {
        lock.lock();
        try
        {
            checkMode();
            sawShutdown = true;
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void sync() throws DataProcessorError
    {
        lock.lock();
        try
        {
            checkMode();
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public UTC resolveUTCTime(final long domclock) throws DataProcessorError
    {
        lock.lock();
        try
        {
            checkMode();
            return null;
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void setRunMonitor(final IRunMonitor runMonitor) throws DataProcessorError
    {
        lock.lock();
        try
        {
            checkMode();
        }
        finally
        {
            lock.unlock();
        }
    }

    private void checkMode()
    {
        switch (mode)
        {
            case NORMAL:
                break;
            case ERROR:
                throw new Error("Mock Error");
            case ONE_ERROR:
                mode = Mode.NORMAL;
                throw new Error("Mock Error");
            case BLOCK_ONCE_INTERRUPTABLY:
                mode = Mode.NORMAL;
                try
                {
                   synchronized (forever)
                   {
                       forever.wait();
                   }
                }
                catch (InterruptedException e)
                {
                    throw new Error("test", e);
                }
                break;
        }
    }

}

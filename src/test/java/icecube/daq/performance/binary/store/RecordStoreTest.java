package icecube.daq.performance.binary.store;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * TestsRecordStore.java
 *
 * Note: Non-deterministic test.
 */
public class RecordStoreTest
{

    // the number of iterations required to force a race condition
    private static int RACINESS = 1000;

    @Test
    public void testUnsychronizedMock() throws InterruptedException
    {
        MockStore mock = new MockStore();
        assertTrue(mock.threadSafe);

        PrunableMethods methods = new PrunableMethods(mock, RACINESS);

        AsyncClient[] clients = new AsyncClient[4];
        for (int i = 0; i < clients.length; i++)
        {
            clients[i] = new AsyncClient(methods);
        }

        for (int i = 0; i < clients.length; i++)
        {
            clients[i].start();
        }

        for (int i = 0; i < clients.length; i++)
        {
            clients[i].join();
        }

        assertFalse(mock.threadSafe);
    }

    @Test
    public void testSyncronizedWritableWrapper() throws InterruptedException
    {
        MockStore mock = new MockStore();

        RecordStore.OrderedWritable synced =
                RecordStore.synchronizedStore((RecordStore.OrderedWritable)mock);

        WritableMethods methods = new WritableMethods(synced, RACINESS);


        AsyncClient[] clients = new AsyncClient[2];
        for (int i = 0; i < clients.length; i++)
        {
            clients[i] = new AsyncClient(methods);
        }

        for (int i = 0; i < clients.length; i++)
        {
            clients[i].start();
        }

        for (int i = 0; i < clients.length; i++)
        {
            clients[i].join();
        }

        assertTrue(mock.threadSafe);
    }

    @Test
    public void testSyncronizedPrunableWrapper() throws InterruptedException
    {
        MockStore mock = new MockStore();

        RecordStore.Prunable synced =
                RecordStore.synchronizedStore(mock);

        PrunableMethods methods = new PrunableMethods(synced, RACINESS);


        AsyncClient[] clients = new AsyncClient[2];
        for (int i = 0; i < clients.length; i++)
        {
            clients[i] = new AsyncClient(methods);
        }

        for (int i = 0; i < clients.length; i++)
        {
            clients[i].start();
        }

        for (int i = 0; i < clients.length; i++)
        {
            clients[i].join();
        }

        assertTrue(mock.threadSafe);
    }


    class WritableMethods implements Runnable
    {
        final RecordStore.OrderedWritable target;
        final int iterations;

        WritableMethods(final RecordStore.OrderedWritable target,
                        final int iterations)
        {
            this.target = target;
            this.iterations = iterations;
        }

        @Override
        public void run()
        {
            try
            {
                for(int i=0; i<iterations; i++)
                {
                    target.extractRange(-1, -1);

                    target.forEach(null, -1, -1);

                    target.store(null);

                    target.available();

                    target.closeWrite();
                }
            }
            catch (IOException e)
            {
                throw new Error(e);
            }
        }
    }

    class PrunableMethods implements Runnable
    {
        final RecordStore.Prunable target;
        final int iterations;

        PrunableMethods(final RecordStore.Prunable target,
                        final int iterations)
        {
            this.target = target;
            this.iterations = iterations;
        }

        @Override
        public void run()
        {
            try
            {
                for(int i=0; i<iterations; i++)
                {
                    target.extractRange(-1, -1);

                    target.forEach(null, -1, -1);

                    target.store(null);

                    target.available();

                    target.prune(-1);

                    target.closeWrite();
                }
            }
            catch (IOException e)
            {
                throw new Error(e);
            }
        }
    }

    class AsyncClient implements Runnable
    {

        boolean error = false;

        Thread thread;
        Runnable methods;

        AsyncClient(final Runnable methods)
        {
            this.methods = methods;
            thread = new Thread(this);
        }

        public void start()
        {
            thread.start();
        }

        public void join() throws InterruptedException
        {
            thread.join();
        }

        @Override
        public void run()
        {
            try
            {
               methods.run();
            }
            catch (Throwable th)
            {
                th.printStackTrace();
                error=true;
            }
        }

    }


    class MockStore implements RecordStore.Prunable
    {
        AtomicInteger threadCount = new AtomicInteger(0);

        boolean threadSafe = true;

        void checkSync()
        {
            if(threadCount.incrementAndGet() != 1)
            {
                threadSafe=false;
            }

            Thread.yield();
            //try{ Thread.sleep(100);} catch (InterruptedException e){}


            if(threadCount.decrementAndGet() != 0)
            {
                threadSafe=false;

            }
        }

        @Override
        public void prune(final long boundaryValue)
        {
           checkSync();
        }

        @Override
        public RecordBuffer extractRange(final long from, final long to)
                throws IOException
        {
            checkSync();
            return null;
        }

        @Override
        public void forEach(final Consumer<RecordBuffer> action,
                            final long from, final long to) throws IOException
        {
            checkSync();
        }

        @Override
        public void store(final ByteBuffer buffer) throws IOException
        {
            checkSync();
        }

        @Override
        public int available()
        {
            checkSync();
            return 0;
        }

        @Override
        public void closeWrite() throws IOException
        {
            checkSync();
        }
    }
}

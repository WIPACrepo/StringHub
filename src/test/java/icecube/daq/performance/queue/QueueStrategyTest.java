package icecube.daq.performance.queue;

import org.jctools.queues.SpmcArrayQueue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.*;


/**
 * Tests QueueStrategy.java
 */
@RunWith(Parameterized.class)
public class QueueStrategyTest
{

    private static int BOUND = 128;

    @Parameterized.Parameter(0)
    public QueueStrategy<Integer> subject;

    @Parameterized.Parameters(name = "QueueStrategy[{0}]")
    public static List<Object[]> sizes()
    {
        List<Object[]> cases = new ArrayList<Object[]>(13);
        cases.add(new Object[]{new QueueStrategy.Blocking<Integer>(new LinkedBlockingQueue<>(BOUND))});

        cases.add(new Object[]{new QueueStrategy.NonBlockingSpin<Integer>(new LinkedBlockingQueue<>(BOUND))});
        cases.add(new Object[]{new QueueStrategy.NonBlockingYield<Integer>(new LinkedBlockingQueue<>(BOUND))});
        cases.add(new Object[]{new QueueStrategy.NonBlockingPoll<Integer>(new LinkedBlockingQueue<>(BOUND), 100)});
        cases.add(new Object[]{new QueueStrategy.NonBlockingPollBackoff<Integer>(new LinkedBlockingQueue<>(BOUND), 100, 500)});

        cases.add(new Object[]{new QueueStrategy.RelaxedSpin<Integer>(new SpmcArrayQueue<>(BOUND))});
        cases.add(new Object[]{new QueueStrategy.RelaxedYield<Integer>(new SpmcArrayQueue<>(BOUND))});
        cases.add(new Object[]{new QueueStrategy.RelaxedPoll<Integer>(new SpmcArrayQueue<>(BOUND), 100)});
        cases.add(new Object[]{new QueueStrategy.RelaxedPollBackoff<Integer>(new SpmcArrayQueue<>(BOUND), 100, 500)});

        return cases;
    }


    @Test
    public void testEnqueue() throws InterruptedException
    {
        for(int i=0; i< BOUND; i++)
        {
            subject.enqueue(i);
        }

        assertEquals(BOUND, subject.size());


        interruptMe(200);
        try
        {
            subject.enqueue(new Integer(BOUND));
            fail("blocking failed");
        }
        catch (InterruptedException e)
        {
            Thread.interrupted();
        }

        for(int i=0; i< BOUND; i++)
        {
            Integer dequeue = subject.dequeue();
            assertEquals(i, dequeue.intValue());
        }

        interruptMe(00);
        try
        {
            subject.dequeue();
            fail("blocking failed");
        }
        catch (InterruptedException e)
        {
            Thread.interrupted();
        }

        assertEquals(0, subject.size());

    }

    private static void interruptMe(long when)
    {
        final Thread target = Thread.currentThread();
        new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    sleep(when);
                    target.interrupt(); }
                catch (InterruptedException e)
                {
                    e.printStackTrace();

                }
            }
        }.start();
    }
}

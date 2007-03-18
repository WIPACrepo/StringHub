package icecube.daq.bindery;

import icecube.daq.splicer.ClosedStrandException;
import icecube.daq.splicer.MonitorPoints;
import icecube.daq.splicer.OrderingException;
import icecube.daq.splicer.Spliceable;
import icecube.daq.splicer.SplicedAnalysis;
import icecube.daq.splicer.Splicer;
import icecube.daq.splicer.SplicerListener;
import icecube.daq.splicer.StrandTail;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class HKN1Splicer implements Splicer, Counter, Runnable
{
    SplicerListener             listener      = null;
    SplicedAnalysis             analysis      = null;
    ArrayList<Node<Spliceable>> exposeList;
    SpliceableComparator        spliceableCmp = new SpliceableComparator();
    Node<Spliceable>            terminalNode  = null;
    volatile int                state         = Splicer.STOPPED;
    volatile int                counter       = 0;
    LinkedList<Spliceable>       rope;
    int                         decrement     = 0;
    
    public HKN1Splicer(SplicedAnalysis analysis)
    {
        this.analysis = analysis;
        exposeList = new ArrayList<Node<Spliceable>>();
        rope = new LinkedList<Spliceable>();
    }

    public void addSpliceableChannel(SelectableChannel channel)
            throws IOException
    {
        throw new UnsupportedOperationException();

    }

    public void addSplicerListener(SplicerListener listener)
    {
        this.listener = listener;
    }

    public void analyze()
    {
        throw new UnsupportedOperationException();
    }

    public StrandTail beginStrand()
    {
        Node<Spliceable> node = new Node<Spliceable>(spliceableCmp, this);
        exposeList.add(node);
        return new HKN1LeafNode(node, this);
    }

    public void dispose()
    {
        // NO OP
    }

    public void forceStop()
    {
        // NO OP
    }

    public SplicedAnalysis getAnalysis()
    {
        return this.analysis;
    }

    public MonitorPoints getMonitorPoints()
    {
        throw new UnsupportedOperationException();
    }

    public int getState()
    {
        return state;
    }

    public String getStateString()
    {
        return getStateString(state);
    }

    public String getStateString(int state)
    {
        if (state == Splicer.STARTED)
            return "STARTED";
        else if (state == Splicer.STOPPED)
            return "STOPPED";
        else if (state == Splicer.STOPPING)
            return "STOPPING";
        return "UNKNOWN";
    }

    public int getStrandCount()
    {
        return counter;
    }

    public List pendingChannels()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public List pendingStrands()
    {
        // TODO Auto-generated method stub
        return null;
    }

    public void removeSpliceableChannel(SelectableChannel channel)
    {
        // TODO Auto-generated method stub

    }

    public void removeSplicerListener(SplicerListener listener)
    {
        // TODO Auto-generated method stub

    }

    public void start()
    {
        // TODO Auto-generated method stub
        state = Splicer.STARTING;
        new Thread(this).start();
    }

    public void start(Spliceable start)
    {
        start();
    }

    public void stop()
    {
        state = Splicer.STOPPING;
    }

    public void stop(Spliceable stop) throws OrderingException
    {
        stop();
    }

    public void truncate(Spliceable spliceable)
    {
        decrement = 0;
        
        while (rope.size() > 0)
        {
            Spliceable x = rope.peek();
            if (spliceable.compareTo(x) < 0) return;
            rope.removeFirst();
            decrement++;
        }

    }

    public void announce(Node<?> node)
    {
        // TODO Auto-generated method stub
        
    }

    public void dec()
    {
        counter--;
    }

    public long getCount()
    {
        return counter;
    }

    public void inc()
    {
        counter++;
    }

    public boolean overflow()
    {
        // TODO Auto-generated method stub
        return false;
    }

    public void run()
    {
        // TODO Auto-generated method stub
        terminalNode = Node.makeTree(exposeList, spliceableCmp, this);
        state = Splicer.STARTED;
        while (state == Splicer.STARTED)
        {
            try
            {
                synchronized (this)
                {
                    this.wait(1000L);
                }
                boolean addedToRope = !terminalNode.isEmpty();
                while (!terminalNode.isEmpty())
                {
                    Spliceable obj = terminalNode.pop();
                    if (obj != Splicer.LAST_POSSIBLE_SPLICEABLE) rope.add(obj);
                }
                if (addedToRope)
                {
                    this.analysis.execute(rope, decrement);
                }
            }
            catch (InterruptedException e)
            {
                // TODO Auto-generated catch block
            }
            
        }
    }

}

class SpliceableComparator implements Comparator<Spliceable>
{

    public int compare(Spliceable s1, Spliceable s2)
    {
        return s1.compareTo(s2);
    }

}

class HKN1LeafNode implements StrandTail
{

    private Node<Spliceable> expose;
    private HKN1Splicer engine;
    
    public HKN1LeafNode(Node<Spliceable> node, HKN1Splicer engine)
    {
        this.expose = node;
        this.engine = engine;
    }

    public void close()
    {
        // intentional no-op
    }

    public Spliceable head()
    {
        return expose.head();
    }

    public boolean isClosed()
    {
        // TODO Auto-generated method stub
        return false;
    }

    public StrandTail push(List spliceables) throws OrderingException,
            ClosedStrandException
    {
        Iterator it = spliceables.listIterator();
        while (it.hasNext()) push((Spliceable) it.next());
        return this;
    }

    public StrandTail push(Spliceable spliceable) throws OrderingException,
            ClosedStrandException
    {
        expose.push(spliceable);
        synchronized (engine)
        {
            engine.notify();
        }
        return this;
    }

    public int size()
    {
        if (expose.isEmpty()) return 0;
        return 1;
    }

}

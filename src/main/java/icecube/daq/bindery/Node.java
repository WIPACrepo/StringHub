package icecube.daq.bindery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Node<T> {

	private Node<T>			sink;
	private Node<T>			peer;
	private LinkedList<T>	list;
	private Comparator<T>	cmp;
	private String			myName;
	private static int		nodeCounter = 0;
	private Counter			cnt;
	private T				val;
	
	public Node(Comparator<T> cmp)
	{
		this(cmp, null);
	}
	
	/**
	 * Two argument constuctor for node specifying comparison class and the counter class
	 * @param cmp node contents are ordered using this Comparator
	 * @param cnt external counter object which can be used to keep track of total number
	 * of objects managed by a collection of Nodes.  If null then this feature is ignored.
	 */
	public Node(Comparator<T> cmp, Counter cnt) 
	{
		sink = null;
		peer = null;
		list = new LinkedList<T>();
		this.cmp = cmp;
		this.cnt = cnt;
		myName = "N" + nodeCounter++;
		val = null;
	}
	
	public void setPeer(Node<T> peer) {	this.peer = peer; }
	
	public void setSink(Node<T> sink) {	this.sink = sink; }
	
	public boolean isEmpty() { return val == null; }
	
	public void setName(String name) { myName = name; }
	
	public String getName() { return myName; }
	
	public T head() { return val; }
	
	public void push(T element) 
	{
		if (cnt != null) cnt.inc();
		if (val != null) 
			list.add(element);
		else
			val = element;
		// System.out.println("Pushed " + element.toString() + " into node " + myName);
		if (sink == null) return;
		while (!isEmpty() && !peer.isEmpty()) {
			if (cmp.compare(head(), peer.head()) > 0) { 
				sink.push(peer.pop()); 
			} else {
				sink.push(pop());
			}
		}
	}
	
	public T pop() 
	{
		if (isEmpty()) return null;
		if (cnt != null) cnt.dec();
		T rval = val;
		// System.out.println("Popped " + rval.toString() + " from node " + myName);
		if (list.size() > 0) 
			val = list.removeFirst();
		else
			val = null;
		return rval;
	}

	/**
	 * Zoink out all stored elements.
	 */
	public void clear()
	{
		list.clear();
		val = null;
	}
	
	/**
	 * Create a sorting tree structure.
	 * @param <T> type of element being pushed through the nodes
	 * @param inputs a list of input nodes
	 * @param cmp the comparator class.  The function cmp.compare() will be
	 * called on the elements in the nodes to determine ordering.
	 * @return
	 */
	public static<T> Node<T> makeTree(List<Node<T>> inputs, Comparator<T> cmp, Counter cnt) 
	{
		ArrayList<Node<T>> copy = new ArrayList<Node<T>>(inputs);
		
        while (copy.size() > 1) 
        {
        	ArrayList<Node<T>> tmp = new ArrayList<Node<T>>();
            Iterator<Node<T>> iter = copy.iterator();
            while (iter.hasNext()) {
                Node<T> a = iter.next();
                Node<T> b;
                Node<T> sink = new Node<T>(cmp, cnt);
                if (iter.hasNext())
                	b = iter.next();
                else 
                {
                    b = a;
                    if (tmp.isEmpty())
                        a = null;
                    else 
                        a = tmp.remove(tmp.size() - 1);
                    sink = new Node<T>(cmp, cnt);
                }

                a.setPeer(b);
                b.setPeer(a);
                a.setSink(sink);
                b.setSink(sink);
                sink.setName("(" + a.myName + "," + b.myName + ")");
                
                // Keep track of the 'position' of the node
                tmp.add(sink);
            }

            copy = tmp;
        }

        return copy.get(0);
		
	}
}

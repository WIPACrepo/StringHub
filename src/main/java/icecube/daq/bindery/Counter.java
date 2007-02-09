package icecube.daq.bindery;

/**
 * A simple interface which originally exposed only an increment/decrement 
 * counter to help arbitrary collections of Nodes keep track of the number
 * of elements contained inside the Nodes.  It has been extended to allow
 * general collection of all nodes in a structure.
 * @author krokodil
 *
 */
public interface Counter 
{
	/**
	 * Increment the counter.
	 */
	public void inc();
	
	/**
	 * Decrement the counter.
	 */
	public void dec();
	
	/**
	 * Get the counter value.
	 * @return the value of the counter.
	 */
	public long getCount();
	
	/**
	 * Returns overfull condition of this counter.
	 * @return true if the counter is too full
	 */
	public boolean overflow();
	
	/**
	 * Announce a new node to the tracker.
	 */
	public void announce(Node<?> node);
}

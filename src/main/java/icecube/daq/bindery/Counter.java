package icecube.daq.bindery;

/**
 * A simple increment/decrement counter interface to help
 * arbitrary collections of Nodes keep track of the number
 * of elements contained inside the Nodes.
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
	public int getCount();
	
	/**
	 * Returns overfull condition of this counter.
	 * @return true if the counter is too full
	 */
	public boolean overflow();
}

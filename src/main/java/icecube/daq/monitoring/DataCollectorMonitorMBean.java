package icecube.daq.monitoring;

/**
 * Monitor MBean for collection of data collectors
 */

public interface DataCollectorMonitorMBean
{
	/**
	 * Get the number of acquired hits.
	 */
	long[] getNumHits();

	/**
	 * Get the number of monitor records
	 */
	long[] getNumMoni();

	/**
	 * Get the number of successful tcals
	 */
	long[] getNumTcal();

	/**
	 * Get the number of supernova packets
	 */
	long[] getNumSupernova();

	/**
	 * Get the run state
	 */
	String[] getRunLevel();

	/**
	 * Get the current acquisition loop count
	 */
	long[] getAcqusitionLoopCount();

	/**
	 * Get the internal node-tracked object counter.
	 */
	long getHitsHKN1Counter();

	/**
	 * Get a count of objects pushed into the HKN1 tree
	 */
	long getHitsHKN1InputCounter();

	/**
	 * Get a count of objects popped from the HKN1 tree
	 */
	long getHitsHKN1OutputCounter();

	/**
	 * Get the internal node-tracked object counter.
	 */
	long getMoniHKN1Counter();

	/**
	 * Get a count of objects pushed into the HKN1 tree
	 */
	long getMoniHKN1InputCounter();

	/**
	 * Get a count of objects popped from the HKN1 tree
	 */
	long getMoniHKN1OutputCounter();

	/**
	 * Get the internal node-tracked object counter.
	 */
	long getTcalHKN1Counter();

	/**
	 * Get a count of objects pushed into the HKN1 tree
	 */
	long getTcalHKN1InputCounter();

	/**
	 * Get a count of objects popped from the HKN1 tree
	 */
	long getTcalHKN1OutputCounter();

	/**
	 * Get the internal node-tracked object counter.
	 */
	long getSupernovaHKN1Counter();

	/**
	 * Get a count of objects pushed into the HKN1 tree
	 */
	long getSupernovaHKN1InputCounter();

	/**
	 * Get a count of objects popped from the HKN1 tree
	 */
	long getSupernovaHKN1OutputCounter();

	long getLastHKN1HitTime();
	long getLastHKN1MoniTime();
	long getLastHKN1TcalTime();
	long getLastHKN1SupernovaTime();
}

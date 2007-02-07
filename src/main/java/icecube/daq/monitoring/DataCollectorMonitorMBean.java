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
}

package icecube.daq.stringhub;

public interface StringHubMBean
{

    /**
     * Report number of functioning DOM channels under control of stringHub.
     * @return number of DOMs
     */
    public int getNumberOfActiveChannels();
    
    /**
     * Report time of the most recent hit object pushed into the HKN1
     * @return
     */
    public long getTimeOfLastHitInputToHKN1();
    
    /**
     * Report time of the most recent hit object output from the HKN1
     * @return
     */
    public long getTimeOfLastHitOutputFromHKN1();
    
    
}

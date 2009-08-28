package icecube.daq.stringhub;

public interface StringHubComponentMBean
{

    /**
     * Report number of functioning DOM channels under control of stringHub.
     * @return number of DOMs
     */
    int getNumberOfActiveChannels();

    /**
     * Report time of the most recent hit object pushed into the HKN1
     * @return
     */
    long getTimeOfLastHitInputToHKN1();

    /**
     * Report time of the most recent hit object output from the HKN1
     * @return
     */
    long getTimeOfLastHitOutputFromHKN1();
}

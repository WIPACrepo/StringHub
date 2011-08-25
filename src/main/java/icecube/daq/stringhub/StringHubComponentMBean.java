package icecube.daq.stringhub;

public interface StringHubComponentMBean
{

    /**
     * Report the total hit rate ( in Hz )
     * @return total hit rate in Hz
     */
    double getHitRate();

    /**
     * Report the lc hit rate ( in Hz )
     * @return lc hit rate in Hz
     */
    double getHitRateLC();


    /**
     * Report number of functioning DOM channels under control of stringHub.
     * @return number of DOMs
     */
    int getNumberOfActiveChannels();

    /**
     * Return an array of the number of active doms and the number of total doms
     * Packed into an integer array to avoid 2 xmlrpc calls from the ActiveDOMsTask
     * @return [0] = number of active doms, [1] = total number of doms
     */
    int[] getNumberOfActiveAndTotalChannels();

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

    /**
     * Return the number of LBM overflows inside this string
     * @return  a long value representing the total lbm overflows in this string
     */
    long getTotalLBMOverflows();
}

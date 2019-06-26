package icecube.daq.sender;

/**
 * Diagnostic counters from the sender and readout subsystem.
 *
 * This class is not explicitly synchronized. Unless otherwise documented,
 * counters are implemented to be modified from a single (counter appropriate)
 * thread and read from another.
 *
 */
public class SenderCounters
{
    /* hit stream */
    volatile long numHitsReceived;
    volatile long latestAcquiredTime;
    volatile boolean isEndOfStream = false;

    /* readout stream*/
    volatile long numReadoutRequestsReceived;
    volatile long numReadoutsSent;
    volatile long numOutputsIgnored;
    volatile long numReadoutErrors;
    public volatile long readoutLatency;

}

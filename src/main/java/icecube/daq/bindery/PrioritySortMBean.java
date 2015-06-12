package icecube.daq.bindery;

public interface PrioritySortMBean
{
    int getChunkSize();
    long getNumberOfChecks();
    long getNumberOfInputs();
    long getNumberOfOutputs();
    long getNumberOfProcessCalls();
    int getQueueSize();
}

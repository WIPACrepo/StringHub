package icecube.daq.domapp;

public enum RunLevel 
{
    /**
     * The data collector is still opening dev files or otherwise initializing.
     */
    INITIALIZING,
    
    /**
     * In the IDLE state the data collector is ready to be configured.
     */
    IDLE,
    
    /**
     * The data collector has gotten a CONFIGURE signal but hasn't fully executed it.
     */
    CONFIGURING,
    CONFIGURED,
    STARTING,
    RUNNING,
    STOPPING,
    ZOMBIE
}

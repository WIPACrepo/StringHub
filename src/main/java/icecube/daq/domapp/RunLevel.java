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
    
    /**
     * Either the DC is just out of CONFIGURING or a run has stopped.
     */
    CONFIGURED,
    
    /**
     * Beginning a run but haven't finished beginning.
     */
    STARTING,
    
    /**
     * Go through the motions to start a subrun.  This requires
     * a fairly intricate dance.
     */
    STARTING_SUBRUN,
    
    /**
     * The data collector is actively taking data from a DOM.
     */
    RUNNING,
    
    /**
     * Has gotten STOP signal but DOM hasn't yet stopped.
     */
    STOPPING,
    STOPPING_SUBRUN,
    /**
     * Has gotten PAUSE signal but DOM hasn't yet stopped.
     * Pausing is like stopping but the output streams are 
     * not flushed (no EOS token inserted).
     */
    PAUSING,

    /**
     * Something is messed up.
     */
    ZOMBIE
}

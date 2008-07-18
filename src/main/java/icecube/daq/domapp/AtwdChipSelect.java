package icecube.daq.domapp;

/**
 * Enumeration for the {@code SELECT_ATWD} message
 * 
 * @author kael
 * @see MessageType#SELECT_ATWD SELECT_ATWD
 *
 */
public enum AtwdChipSelect 
{
    /**
     * Only use ATWD A
     */
    ATWD_A, 
    
    /**
     * Only use ATWD B
     */
    ATWD_B, 
    
    /**
     * Operate in 'ping-pong' mode (default)
     */
    PING_PONG;
}

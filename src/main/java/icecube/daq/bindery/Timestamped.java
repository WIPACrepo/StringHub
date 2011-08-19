package icecube.daq.bindery;

import icecube.daq.util.UTC;

public interface Timestamped 
{
    /**
     * The time method returns a UTC object which holds the records timestamp.
     * @return UTC object - representing the time of the particular event
     */
    UTC time();
}

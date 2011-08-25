package icecube.daq.dor;

public class GPSNotReady extends GPSException
{
    private static final long serialVersionUID = 1L;
    private int bytesRead;
    
    public GPSNotReady(String procfile, int n)
    {
        super(procfile);
        bytesRead = n;
    }
    
    public String toString()
    {
        return "GPS procfile " + procfile + " not ready (" + 
            bytesRead + " read).";
    }
}

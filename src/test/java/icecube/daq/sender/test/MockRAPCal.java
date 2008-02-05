package icecube.daq.sender.test;



import icecube.daq.dor.TimeCalib;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.util.UTC;

class MockRAPCal
    implements RAPCal
{
    private UTC gpsOffset;

    public double clockRatio()
    {
        throw new Error("Unimplemented");
    }

    public double cableLength()
    {
        throw new Error("Unimplemented");
    }

    public UTC domToUTC(long domclk)
    {
        return new UTC(domclk);
    }

    public void update(TimeCalib tcal, UTC gpsOffset)
        throws RAPCalException
    {
        // do nothing
    }
    
    public boolean laterThan(long clk)
    {
        return true;
    }
}

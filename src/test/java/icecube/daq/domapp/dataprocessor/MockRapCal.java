package icecube.daq.domapp.dataprocessor;

import icecube.daq.dor.TimeCalib;
import icecube.daq.monitoring.IRunMonitor;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.util.UTC;

/**
 * Simulates a rapcal time converter, exposing
 * the offset and upper bound for manipulation.
 */
public class MockRapCal implements RAPCal
{

    public long offset;
    public long upperBound;

    public MockRapCal(final long offset)
    {
        this.offset = offset;
        this.upperBound = Long.MIN_VALUE;
    }

    // manipulators

    public void setOffset(long offset)
    {
        this.offset = offset;
    }

    public void adjustOffset(long adjustment)
    {
        offset+= adjustment;
    }

    public void setUpperBound(long upperBound)
    {
        this.upperBound = upperBound;
    }


    // rapcal

    @Override
    public UTC domToUTC(final long domclk)
    {
        return new UTC(250L * domclk + offset);
    }

    @Override
    public double cableLength()
    {
        return 0;
    }

    @Override
    public double epsilon()
    {
        return 0;
    }

    @Override
    public boolean isReady()
    {
        return false;
    }

    @Override
    public boolean laterThan(final long domclk)
    {
        return domclk <= upperBound;
    }

    @Override
    public void setMainboardID(long mbid)
    {
    }

    @Override
    public void setRunMonitor(final IRunMonitor runMonitor)
    {
    }

    @Override
    public boolean update(final TimeCalib tcal, final UTC gpsOffset)
            throws RAPCalException
    {
        return true;
    }


}

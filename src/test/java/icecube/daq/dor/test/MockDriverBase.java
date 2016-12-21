package icecube.daq.dor.test;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.TimeCalib;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
 * Sets up a base DOR driver to be extended by Mocks.
 */
public class MockDriverBase implements IDriver
{
    @Override
    public File getTCALFile(final int card, final int pair, final char dom)
    {
        return null;
    }

    @Override
    public File getGPSFile(final int card)
    {
        return null;
    }

    @Override
    public GPSInfo readGPS(final File gpsFile) throws GPSException
    {
        return null;
    }

    @Override
    public TimeCalib readTCAL(final File tcalFile) throws IOException, InterruptedException
    {
        return null;
    }

    @Override
    public void softboot(final int card, final int pair, final char dom) throws IOException
    {
    }

    @Override
    public void commReset(final int card, final int pair, final char dom) throws IOException
    {
    }

    @Override
    public void setBlocking(final boolean block) throws IOException
    {
    }

    @Override
    public HashMap<String, Integer> getFPGARegisters(final int card) throws IOException
    {
        return null;
    }

    @Override
    public void resetComstat(final int card, final int pair, final char dom) throws IOException
    {
    }

    @Override
    public String getComstat(final int card, final int pair, final char dom) throws IOException
    {
        return null;
    }

    @Override
    public String getFPGARegs(final int card) throws IOException
    {
        return null;
    }

    @Override
    public String getProcfileID(final int card, final int pair, final char dom) throws IOException
    {
        return null;
    }
}

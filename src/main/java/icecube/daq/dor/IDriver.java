package icecube.daq.dor;

import java.io.IOException;

public interface IDriver
{
    GPSInfo readGPS(int card)
        throws GPSException;

    TimeCalib readTCAL(int card, int pair, char dom)
        throws IOException, InterruptedException;
}

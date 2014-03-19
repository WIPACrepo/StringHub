package icecube.daq.dor;

import java.io.IOException;

import icecube.daq.juggler.alert.Alerter;

public interface IGPSService
{
    GPSInfo getGps(int card) throws IOException;

    void startService(int card)  throws IOException;

    void shutdownAll()  throws IOException;

    void setAlerter(Alerter alerter)  throws IOException;
}

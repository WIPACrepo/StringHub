package icecube.daq.time.gps;

import java.io.IOException;

import icecube.daq.dor.GPSInfo;
import icecube.daq.juggler.alert.Alerter;

public interface IGPSService
{
    GPSInfo getGps(int card) throws IOException;

    void startService(int card)  throws IOException;

    void shutdownAll()  throws IOException;

    void setAlerter(Alerter alerter)  throws IOException;
}

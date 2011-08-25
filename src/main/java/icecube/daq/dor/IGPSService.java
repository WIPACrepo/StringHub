package icecube.daq.dor;

import icecube.daq.juggler.alert.Alerter;

import java.io.IOException;

public interface IGPSService
{ 
    GPSInfo getGps(int card) throws IOException;
    
    void startService(int card)  throws IOException;
    
    void shutdownAll()  throws IOException;

    void setAlerter(Alerter alerter) throws IOException;

}

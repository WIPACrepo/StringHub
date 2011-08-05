package icecube.daq.dor;

import java.util.GregorianCalendar;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import java.io.IOException;

import icecube.daq.juggler.alert.Alerter;
import icecube.daq.util.StringHubAlert;


public interface IGPSService
{ 
    GPSInfo getGps(int card) throws IOException;
    
    void startService(int card)  throws IOException;
    
    void shutdownAll()  throws IOException;
	
    void setAlerter(Alerter alerter)  throws IOException;

}

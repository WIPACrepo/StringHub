package icecube.daq.domapp.dataprocessor;

import icecube.daq.dor.GPSInfo;
import icecube.daq.time.gps.GPSService;
import icecube.daq.time.gps.GPSServiceError;
import icecube.daq.time.gps.IGPSService;


/**
 * Provides GPSInfo to the data processor.
 */
public class GPSProvider
{
    private final int card;
    private final IGPSService service;

    public GPSProvider(final int card)
    {
        this.card = card;
        this.service = GPSService.getInstance();
    }

    public GPSInfo getGPSInfo() throws DataProcessorError
    {
        try
        {
            return service.getGps(card);
        }
        catch (GPSServiceError gpsError)
        {
            throw new DataProcessorError("GPS service failed for card" +
                    " [" + card + "]", gpsError);
        }
    }
}

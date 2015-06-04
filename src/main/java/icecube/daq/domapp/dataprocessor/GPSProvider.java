package icecube.daq.domapp.dataprocessor;

import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.GPSService;


/**
 * Provides GPSInfo to the data processor.
 */
public class GPSProvider
{
    private final int card;
    private final GPSService service;

    public GPSProvider(final int card)
    {
        this.card = card;
        this.service = GPSService.getInstance();
    }

    public GPSInfo getGPSInfo() throws DataProcessorError
    {
        GPSInfo gps = service.getGps(card);
        if(gps == null)
        {
            throw new DataProcessorError("Could not get GPSInfo for card" +
                    " [" + card + "]");
        }
        else
        {
            return gps;
        }
    }
}

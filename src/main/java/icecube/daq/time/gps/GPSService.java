package icecube.daq.time.gps;

import org.apache.log4j.Logger;

/**
 * Factory providing system-wide access to the GPS service.
 * <p>
 * The GPS service builds on the DOR GPS snapshot buffer providing:
 * <ul>
 *     <li>One-to-many fan out of GPS snapshots from card to channel level</li>
 *     <li>Enforces stability invariant </li>
 *     <li>Monitoring, error handling and alerting</li>
 * </ul>
 * <p>
 * In normal deployments, the service will be a backed by a
 * DSB GPS card. Certain test deployments configured without
 * GPS hardware may configure this factory to provide a fallback
 * service. The fallback service does not provide meaningful UTC
 * time reconstructions.
 * <p>
 * Configuration
 * <pre>
 *
 *    icecube.daq.dor.gps-mode = [dsb]
 *
 *           dsb:      The master clock will be used via DSB card.
 *           no-dsb:   No GPS hardware, time reconstruction assumes zero DOR
 *                     clock offset.
 *           discover: Will use GPS hardware if available, falling back to
 *                     the no-dsb mode.
 *
 *</pre>
 *
 */

public class GPSService
{

    private static final Logger logger = Logger.getLogger(GPSService.class);

    /**
     * Configures the GPS mode, one of dsb, no-dsb, discover.
     */
    public static final String GPS_MODE =
            System.getProperty("icecube.daq.dor.gps-mode", "dsb");

    /** The singleton, system wide service instance. */
    private static final IGPSService service;

    // initialize the service
    static
    {
        if(GPS_MODE.equalsIgnoreCase("dsb"))
        {
            service = new DSBGPSService();
        }
        else if(GPS_MODE.equalsIgnoreCase("no-dsb"))
        {
            // This is not an appropriate production mode so log
            // a warning
            logger.warn("Running without GPS hardware," +
                    " UTC reconstruction will be impacted.");
            service = new NullGPSService();
        }
        else if(GPS_MODE.equalsIgnoreCase("discover"))
        {
            // This is not an appropriate production mode so log
            // a warning
            logger.warn("Running in relaxed GPS hardware mode," +
                    " UTC reconstruction may be impacted.");
            service = new FailsafeGPSService( new DSBGPSService(),
                    new NullGPSService());
        }
        else
        {
            throw new Error("Unknown GPS mode: [" + GPS_MODE + "]");
        }
    }


    /**
     * Provides access the configured GPS service.
     *
     * @return The GPS service.
     */
    public static IGPSService getInstance() { return service; }


}

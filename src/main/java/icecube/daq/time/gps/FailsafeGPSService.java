package icecube.daq.time.gps;

import icecube.daq.dor.GPSInfo;
import icecube.daq.monitoring.IRunMonitor;
import org.apache.log4j.Logger;

/**
 * A convenience implementation of the GPS service which can be used
 * to discover if the GPS hardware is present and fall back to an alternative
 * if the hardware is not present.
 * <p>
 * Note:
 * Not for production use. Provided for test deployments.
 *<p>
 * Note:
 * Adaptive behavior is based on the first card activated.  Systems with GPS
 * support on some DOR cards and not others should be explicitly configured
 * rather than dynamically configured.
 */
public class FailsafeGPSService implements IGPSService
{

    private final static Logger logger =
            Logger.getLogger(FailsafeGPSService.class);

    /**
     * The services which will be attempted.
     */
    private final IGPSService primary;
    private final IGPSService fallback;

    /** The first service to establish as ready. */
    private IGPSService delegate;


    /**
     * Package protected construction.
     * @param primary The first service to attempt, usually the hardware backed
     *                implementation.
     * @param fallback If the primary service can not be established, the
     *                 fallback will be used.
     */
    FailsafeGPSService(final IGPSService primary, final IGPSService fallback)
    {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public GPSInfo getGps(final int card) throws GPSServiceError
    {
        synchronized(this)
        {
            if(delegate != null)
            {
                return delegate.getGps(card);
            }
            else
            {
                throw new GPSServiceError("Can not use service until started" +
                        " for card " + card);
            }
        }
    }

    @Override
    public void startService(final int card)
    {
        synchronized(this)
        {
            if(delegate == null)
            {
                delegate = discover(card);
            }
            else
            {
                delegate.startService(card);
            }
        }
    }

    @Override
    public boolean waitForReady(final int card, final int waitMillis)
            throws InterruptedException
    {
        synchronized(this)
        {
            if(delegate != null)
            {
                return delegate.waitForReady(card, waitMillis);
            }
            else
            {
                return false;
            }
        }
    }

    @Override
    public void shutdownAll()
    {
        synchronized(this)
        {
            if(delegate != null)
            {
                delegate.shutdownAll();
            }
        }
    }

    @Override
    public void setRunMonitor(final IRunMonitor runMonitor)
    {
        synchronized(this)
        {
            if(delegate != null)
            {
                delegate.setRunMonitor(runMonitor);
            }
        }
    }

    @Override
    public void setStringNumber(final int string)
    {
        synchronized(this)
        {
            if(delegate != null)
            {
                delegate.setStringNumber(string);
            }
        }
    }

    /**
     * Discovers the best GPS option by trying the primary
     * service and then falling back to the fallback.
     *
     * @param card The card to utilize for discovery.
     * @return The service selected for use.
     */
    private IGPSService discover(int card)
    {
        primary.startService(card);

        boolean primaryReady = false;
        try
        {
            primaryReady = primary.waitForReady(card, 5000);
        }
        catch (InterruptedException e)
        {
            primaryReady = false;
        }

        if(primaryReady)
        {
            return primary;
        }
        else
        {
            primary.shutdownAll();

            // Generally the primary is hardware and the fallback is the
            // null service. Since the null service impacts data quality
            // fallback results in a warning
            logger.warn("Primary GPS service ["+ primary.getClass() +
                    "] not running, switching to fallback GPS" +
                    " service: [" + fallback.getClass() + "]");
            fallback.startService(card);
            return fallback;
        }
    }

}

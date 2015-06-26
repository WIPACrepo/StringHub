package icecube.daq.time.monitoring;

import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.Alerter;

import java.util.ArrayList;
import java.util.List;

/**
 * Intercept Alerts for testing.
 */
public class MockAlerter implements Alerter
{
    volatile boolean isOpen = true;
    List<Object> alerts = new ArrayList<Object>(5);

    void waitForClose()
    {
        int tries = 10;
        while(isOpen && (--tries > 0))
        {
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                //
            }
        }
        if(isOpen)
        {
            throw new Error("AlertQueue was not closed.");
        }
    }

    @Override
    public void close()
    {
        isOpen = false;
    }

    @Override
    public String getService()
    {
        return "ClockAlerterTest";
    }

    @Override
    public boolean isActive()
    {
        return isOpen;
    }

    @Override
    public void sendObject(final Object obj) throws AlertException
    {
        alerts.add(obj);
    }

    @Override
    public void setAddress(final String host, final int port)
            throws AlertException
    {
    }
}
package icecube.daq.util;

import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.Alerter;

import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class StringHubAlert
{
    /** Logging object */
    private static final Log LOG = LogFactory.getLog(StringHubAlert.class);


    /**
     * Send an alert that the leapsecond file has expired
     */
    public static final void sendLeapsecondExpired(Alerter alerter, String condition,
						   String desc, double days_past_expiry) {
        if (alerter == null || !alerter.isActive()) {
            return;
        }

        HashMap<String, Object> vars = new HashMap<String, Object>();
        if (days_past_expiry!=0) {
            vars.put("days_past_expiry", days_past_expiry);
        }

        try {
            alerter.send(Alerter.PRIO_SCP, condition, desc, vars);
        } catch (AlertException ae) {
            LOG.error("Cannot send " + condition + " alert", ae);
	}	

    }


    /**
     * Send a DOM alert.
     */
    public static final void sendDOMAlert(Alerter alerter, String condition,
                                          String desc, int card, int pair,
                                          char dom, String mbid, String name,
                                          int major, int minor)
    {
        if (alerter == null || !alerter.isActive()) {
            return;
        }

        HashMap<String, Object> vars = new HashMap<String, Object>();
        if (dom != (char) 0) {
            vars.put("card", card);
            vars.put("pair", pair);
            vars.put("dom", dom);
        }
        if (mbid != null) {
            vars.put("mbid", mbid);
        }
        if (name != null) {
            vars.put("name", name);
        }
        vars.put("major", major);
        vars.put("minor", minor);

        try {
            alerter.send(Alerter.PRIO_SCP, condition, desc, vars);
        } catch (AlertException ae) {
            LOG.error("Cannot send " + condition + " alert", ae);
        }
    }
}


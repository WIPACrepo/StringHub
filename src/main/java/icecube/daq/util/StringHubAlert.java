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
     * Send a DOM alert.
     */
    public static final void sendDOMAlert(Alerter alerter, String type,
                                          String condition, int card, int pair,
                                          char dom, String mbid, String name,
                                          int major, int minor)
    {
        if (alerter == null || !alerter.isActive()) {
            return;
        }

        HashMap<String, Object> vars = new HashMap<String, Object>();
        vars.put("card", card);
        vars.put("pair", pair);
        vars.put("dom", dom);
        if (mbid != null) {
            vars.put("mbid", mbid);
        }
        if (name != null) {
            vars.put("name", name);
        }
        vars.put("major", major);
        vars.put("minor", minor);

        try {
            alerter.send(type, Alerter.PRIO_ITS, condition, vars);
        } catch (AlertException ae) {
            LOG.error("Cannot send " + type + " alert", ae);
        }
    }

}
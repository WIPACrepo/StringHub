package icecube.daq.util;

import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.juggler.alert.Alerter.Priority;
import icecube.daq.payload.impl.UTCTime;

import java.util.HashMap;

import org.apache.log4j.Logger;

public class StringHubAlert
{
    /** Logging object */
    private static final Logger LOG = Logger.getLogger(StringHubAlert.class);

    /** Default alert priority */
    public static final Priority DEFAULT_PRIORITY = Priority.SCP;

    /** Placeholder for alerts without a card number */
    public static final int NO_CARD = Integer.MIN_VALUE;

    /** Placeholder for alerts without a pair number */
    public static final int NO_PAIR = Integer.MIN_VALUE;

    /** Placeholder for alerts without an A/B DOM specifier */
    public static final char NO_SPECIFIER = (char) 0;

    /** Placeholder for alerts without a run number */
    public static final int NO_RUNNUMBER = Integer.MIN_VALUE;

    /** Placeholder for alerts without a DAQ time */
    public static final long NO_UTCTIME = Long.MIN_VALUE;

    /**
     * Send a DOM alert.
     */
    public static final void sendDOMAlert(AlertQueue alertQueue,
                                          Priority priority, String condition,
                                          int card, int pair, char dom,
                                          String mbid, String name,
                                          int string, int position,
                                          int runNumber, long utcTime)
    {
        if (alertQueue == null || alertQueue.isStopped()) {
            return;
        }

        HashMap<String, Object> vars = new HashMap<String, Object>();
        if (dom != NO_SPECIFIER) {
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
        vars.put("string", string);
        vars.put("position", position);
        if (runNumber != NO_RUNNUMBER) {
            vars.put("runNumber", runNumber);
        }
        if (utcTime > 0) {
            vars.put("exact-time", UTCTime.toDateString(utcTime));
        } else if (utcTime != -1 && utcTime != NO_UTCTIME) {
            LOG.warn("Ignoring unexpected negative UTC time " + utcTime);
        }

        HashMap values = new HashMap();
        if (condition != null && condition.length() > 0) {
            values.put("condition", condition);
        }
        values.put("vars", vars);

        try {
            alertQueue.push("alert", priority, values);
        } catch (AlertException ae) {
            LOG.error("Cannot send " + condition + " alert", ae);
        }
    }
}

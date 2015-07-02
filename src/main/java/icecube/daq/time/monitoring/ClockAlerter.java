package icecube.daq.time.monitoring;

import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.juggler.alert.Alerter;
import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages Clock alerts.
 */
public class ClockAlerter
{
    private final Logger logger = Logger.getLogger(ClockAlerter.class);

    /** Delivers alerts to live. */
    private final AlertQueue alertQueue;

    /** Minimum interval between alerts of the same type. */
    private final long minimumAlertIntervalNanos;

    /** Monotonic timestamp of last local clock offset alert. */
    private long lastSystemClockOffsetAlertNanos;

    /** Monotonic timestamp of last master clock offset alert. */
    private long lastMasterClockOffsetAlertNanos;

    /** Monotonic timestamp of last master clock quality alert. */
    private long lastMasterClockQualityAlertNanos;

    /** Monotonic timestamp of last NTP server alert. */
    private long lastNTPServerAlertNanos;

    /** Email address that will be sent a copy of the alert. */
    private final String email;

    /** Indicates if the alert should request a page. */
    private final boolean requestPage;


    /**
     * Construct the alerter for the Clock Monitor subsystem.
     *
     * @param alertQueue Target for the alerts.
     * @param email The email address to receive alerts, use the empty
     *              string to suppress emails.
     * @param requestPage If true, alerts will request a page.
     * @param minimumAlertIntervalMinutes During this period, at most one
     *                                    alert of a type will be issued.
     */
    ClockAlerter(final AlertQueue alertQueue,
                 final String email,
                 final boolean requestPage,
                 final int minimumAlertIntervalMinutes)
    {
        this.alertQueue = alertQueue;
        this.email = email;
        this.requestPage = requestPage;

        this.minimumAlertIntervalNanos =
                minimumAlertIntervalMinutes * 60 * 1000000000L;

        // set the last alerts to a value over an interval in the past
        long base = now() - minimumAlertIntervalNanos - 1;
        lastSystemClockOffsetAlertNanos = base;
        lastMasterClockOffsetAlertNanos = base;
        lastMasterClockQualityAlertNanos = base;
        lastNTPServerAlertNanos = base;
    }

    void alertSystemClockOffset(final double offsetMillis, final String ntpID)
    {
        final long now = now();

        if(now > (lastSystemClockOffsetAlertNanos + minimumAlertIntervalNanos))
        {
            logger.warn(String.format("System Clock offset [%f ms] from" +
                    " reference NTP [%s]", offsetMillis, ntpID));

            final String hostname = getHostname();

            final String emailSubject =
                    String.format("The System Clock is out of agreement with" +
                            " the NTP reference");
            final String emailBody =
                    String.format("The System Clock on [%s] is out of" +
                            " agreement with the NTP reference [%s] by [%f ms]",
                            hostname, ntpID, offsetMillis);

            final String condition =
                    "System Clock is out of agreement with the NTP reference";

            final String description =
                    String.format("The System Clock on [%s] is out of" +
                            " agreement with the NTP reference [%s] by [%f ms]",
                            hostname, ntpID, offsetMillis);

            HashMap<String, Object> values = new HashMap<String, Object>();
            values.put("system", hostname);
            values.put("offset", offsetMillis);
            values.put("ntp-source", ntpID);

            sendAlert(condition, description, emailSubject, emailBody, values);

            lastSystemClockOffsetAlertNanos = now;
        }
    }

    void alertMasterClockOffset(final double offsetMillis, final String ntpID)
    {
        final long now = now();

        if(now > (lastMasterClockOffsetAlertNanos + minimumAlertIntervalNanos))
        {
            logger.warn(String.format("Master Clock offset [%f ms] from" +
                    " reference NTP [%s]", offsetMillis, ntpID));

            final String hostname = getHostname();

            final String emailSubject="The Master Clock is out of agreement" +
                    " with the NTP reference";

            final String emailBody=
                    String.format("The Master Clock on [%s] is" +
                    " offset from the reference NTP server [%s] by [%f ms]",
                    hostname, ntpID, offsetMillis);

            HashMap<String, Object> values = new HashMap<String, Object>();
            values.put("system", hostname);
            values.put("offset", offsetMillis);
            values.put("ntp-source", ntpID);


            final String condition = "Master Clock is out of agreement with" +
                    " the NTP reference";

            final String description =
                    String.format("The Master Clock on [%s] is" +
                    " offset from the reference NTP server [%s] by [%f ms]",
                    hostname, ntpID, offsetMillis);

            sendAlert(condition, description, emailSubject, emailBody, values);

            lastMasterClockOffsetAlertNanos = now;
        }
    }

    void alertMasterClockQuality(final int quality)
    {
        final long now = now();

        if(now > (lastMasterClockQualityAlertNanos + minimumAlertIntervalNanos))
        {
            logger.warn(String.format("Master Clock quality degraded [%d]",
                    quality));

            final String hostname = getHostname();

            final String emailSubject="The Master Clock Quality has degraded";

            final String emailBody=
                    String.format("The Master Clock quality on [%s]" +
                    " is degraded. The current quality marker is [%s]",
                    hostname, intToQuality(quality));

            final String condition = "Master Clock Quality is degraded";

            final String description =
                    String.format("The Master Clock quality on [%s]" +
                    " is degraded. The current quality marker is [%s]",
                    hostname, intToQuality(quality));


            HashMap<String, Object> values = new HashMap<String, Object>();
            values.put("system", hostname);
            values.put("quality", quality);

            sendAlert(condition, description, emailSubject, emailBody, values);

            lastMasterClockQualityAlertNanos = now;
        }
    }

    void alertNTPServer(final String reason, final String ntpID)
    {
        final long now = now();

        if(now > (lastNTPServerAlertNanos + minimumAlertIntervalNanos))
        {
            logger.warn("Problem acquiring from the NTP source: " + reason);

            final String hostname = getHostname();

            final String emailSubject =
                    String.format("Clock monitoring is unable to access" +
                            " the reference NTP server");

            final String emailBody =
                    String.format("An error occured while accessing the NTP" +
                            " server [%s] from [%s]: [%s]", ntpID, hostname,
                            reason);

            final String condition =
                    "Problem acquiring from the NTP source";

            final String description =
                    String.format("An error occured while accessing the NTP" +
                            " server [%s] from [%s]: [%s]", ntpID, hostname,
                            reason);

            HashMap<String, Object> values = new HashMap<String, Object>();
            values.put("system", hostname);
            values.put("ntp-source", ntpID);

            sendAlert(condition, description, emailSubject, emailBody, values);

            lastNTPServerAlertNanos = now;
        }
    }

    private void sendAlert(final String condition, final String description,
                           final String emailSubject, final String emailBody,
                           final Map<String, Object> vars)
    {
        // set up the alert structure
        final Map<String, Object> valueMap = new HashMap<String, Object>();
        valueMap.put("condition", condition);
        valueMap.put("desc", description);

        // add email if defined
        if(email != null && !email.equals(""))
        {
            Map<String, String> emailData = new HashMap<String, String>();
            emailData.put("receiver" , email);
            emailData.put("subject", emailSubject);
            emailData.put("body", emailBody);
            valueMap.put("dbnotify", emailData);
        }

        // add request page if configured
        if(requestPage)
        {
            valueMap.put("pages", requestPage);
        }

        // add variables if defined
        if(vars != null)
        {
            valueMap.put("vars", vars);
        }

        //  queue for sending
        try
        {
            alertQueue.push("alert", Alerter.Priority.EMAIL, valueMap);
        }
        catch (AlertException ae)
        {
            logger.error("Error sending clock monitoring alert", ae);
        }
        catch (Throwable th)
        {
            logger.error("Error sending clock monitoring alert", th);
        }
    }

    /**
     * Lookup the name of the local system.
     */
    private String getHostname()
    {
        try
        {
            return InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e)
        {
            // well that's unfortunate, but don't let it
            // spoil the alert
            return "n/a";
        }
    }

    /**
     * Convert from an int, derived originally from the quality character
     * in the GPS string, into the quality description.
     *
     * @param quality The ascii code point value of quality indicator character.
     *
     * @return A description phrase for the quality level.
     */
    private String intToQuality(int quality)
    {
        switch (quality)
        {
            case (int)' ':
                return "VERY GOOD ("+ (char)quality + ")";
            case (int)'.':
                return "GOOD ("+ (char)quality + ")";
            case (int)'*':
                return "AVERAGE ("+ (char)quality + ")";
            case (int)'#':
                return "BAD ("+ (char)quality + ")";
            case (int)'?':
                return "VERY BAD ("+ (char)quality + ")";
            default:
                return "UNKNOWN(" + quality +")";
        }
    }

    /**
     * Take a timestamp from the system monotonic timer.
     */
    private long now()
    {
        return System.nanoTime();
    }


}

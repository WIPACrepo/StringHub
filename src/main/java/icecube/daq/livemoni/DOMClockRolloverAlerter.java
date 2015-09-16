package icecube.daq.livemoni;

import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.stringhub.AlertFactory;
import icecube.daq.util.TimeUnits;
import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Examines DOM clock values and issues user alerts for pending
 * DOM clock rollovers.
 * <p>
 * Alerts are issued at two thresholds:
 * <p>
 * When the second threshold is reached, a general warning is issued.
 * This alert utilizes the throttling mechanism of i3live with a throttling
 * token composed of the month and year. This limits the number DOM clock
 * warning emails to one per month.
 * <p>
 * When the second threshold is reached, another alert is issued. This
 * alert is not throttled. Alerts will be issued on each run for every
 * hub that has DOM clock exceeding the threshold.
 * <p>
 * Configuration:
 * <pre>
 *   dom-clock-warning-alert-days = [65]
 *
 *       The warning threshold in days.
 *
 *   dom-clock-warning-alert-days = [78]
 *
 *       The critical threshold in days.
 *
 * </pre>

 *
 */
public class DOMClockRolloverAlerter
{

    private static final Logger logger =
            Logger.getLogger(DOMClockRolloverAlerter.class);

    /** The DOM clock value that triggers a first alert. */
    public static final int DOM_UPTIME_WARN_THRESHOLD_DAYS =
            Integer.getInteger("icecube.daq.livemoni.dom-clock-warning-alert-days", 65);

    /** The DOM clock value that triggers a second alert. */
    public static final int DOM_UPTIME_CRITICAL_THRESHOLD_DAYS =
            Integer.getInteger("icecube.daq.livemoni.dom-clock-critical-alert-days", 78);

    /** Defines the throttling domain. */
    public static final String THROTTLE_TOKEN_PREFIX =
            System.getProperty("icecube.daq.livemoni.dom-clock-throttle-token",
                    "dom-clock-alert");

    /** Nanoseconds per day. */
    private final static long NANOS_PER_DAY = 24 * 60 * 60 * 1000000000L;

    /** Warning threshold in nanoseconds. */
    private static final long DOM_UPTIME_WARN_THRESHOLD_NANOS =
            DOM_UPTIME_WARN_THRESHOLD_DAYS * NANOS_PER_DAY;

    /** Critical threshold in nanoseconds. */
    private static final long DOM_UPTIME_CRITICAL_THRESHOLD_NANOS =
            DOM_UPTIME_CRITICAL_THRESHOLD_DAYS * NANOS_PER_DAY;




    /**
     * Examine the clock value of a set of DOMS and issue an alert if
     * thresholds are exceeded.
     *
     * @param alertQueue The alert queue.
     * @param records A map of dom mbid strings that map to the current dom
     *                clock value;
     * @throws AlertException An error occurred.
     */
    public void monitorDOMClocks(final AlertQueue alertQueue,
                                 final Map<DOMChannelInfo, Long> records)
            throws AlertException
    {
        boolean isCritical = false;
        final Map<DOMChannelInfo, Long> overThreshold =
                new HashMap<DOMChannelInfo, Long>(records.size());
        for (Map.Entry<DOMChannelInfo, Long> dom : records.entrySet())
        {
            long clock = dom.getValue();
            if(TimeUnits.DOM.asUTC(clock) > DOM_UPTIME_WARN_THRESHOLD_NANOS)
            {
                overThreshold.put(dom.getKey(), clock);
                if(TimeUnits.DOM.asUTC(clock) >
                        DOM_UPTIME_CRITICAL_THRESHOLD_NANOS)
                {
                    isCritical = true;
                }
            }
        }

        if(overThreshold.size() > 0)
        {


            final Map<String, Object> content;
            if(isCritical)
            {
                for(Map.Entry<DOMChannelInfo, Long> record : overThreshold.entrySet())
                {
                    String msg = String.format("DOM %d%d%s mbid %s is" +
                            " approaching the clock rollover, clock [%d]",
                            record.getKey().card,
                            record.getKey().pair,
                            record.getKey().dom,
                            record.getKey().mbid,
                            record.getValue());
                    logger.warn(msg);
                }
                content = buildCriticalAlert(overThreshold);
            }
            else
            {
                content = buildWarningAlert(overThreshold);
            }

            AlertFactory.sendEmailAlert(alertQueue, content);
        }
    }

    /**
     * Build the warning email alert object.
     *
     * The warning email is throttled, therefore the message is generalized
     * to represent a detector wide condition.
     *
     * Package scope for testing.
     */
    Map<String, Object> buildWarningAlert(final Map<DOMChannelInfo,
            Long> records)
    {
        final String throttleToken = getThrottleToken();
        final String condition = "Approaching a Scheduled DOM Softboot";
        final String description = "One or more DOMS are approaching a" +
                " required softboot";
        final String subject = description;

        final StringBuilder emailBody = new StringBuilder(1024);
        emailBody.append("One or more DOMS are approaching a" +
                " required softboot.");


        final Map<String, Object> vars =
                new HashMap<String, Object>(records.size());
        for (Map.Entry<DOMChannelInfo, Long> record : records.entrySet())
        {
            vars.put(record.getKey().mbid, record.getValue());
        }

        return AlertFactory.constructEmailAlert(condition,
                description, subject, emailBody.toString(),
                throttleToken, vars);
    }

    /**
     * Build the critical email alert object.
     *
     * The critical email is not throttled, therefore the message contains
     * details of which DOMS clocks are critical.
     *
     * Package scope for testing.
     */
    Map<String, Object> buildCriticalAlert(
            final Map<DOMChannelInfo, Long> records)
    {
        final String hubID = getHostname();

        final String condition = "Approaching a DOM Clock Rollover";
        final String description = String.format("One or more DOMS on" +
                " hub [%s] are approaching a clock rollover", hubID);

        final String subject = description;

        final StringBuilder emailBody = new StringBuilder(1024);
        emailBody.append(String.format("The following DOMS on" +
                " hub [%s] are approaching a clock rollover.%n" +
                "DOMS should be softbooted to avoid disruption.%n%n", hubID));

        emailBody.append(String.format("%-14s %-8s %8s%n", "DOM",
                "CHANNEL", "CLOCK"));
        final Map<String, Object> vars =
                new HashMap<String, Object>(records.size());
        for (Map.Entry<DOMChannelInfo, Long> record : records.entrySet())
        {
            final DOMChannelInfo dom = record.getKey();
            final Long clock = record.getValue();
            emailBody.append(String.format("%-14s %d%d%s %8s days%n",
                    dom.mbid,
                    dom.card,
                    dom.pair,
                    dom.dom,
                    domToDays(clock)));
            vars.put(dom.mbid, clock);

        }

        return AlertFactory.constructEmailAlert(condition,
                description, subject, emailBody.toString(),
                null, vars);
    }

    /**
     * Construct the throttling token.
     * @return The throttling token, composed of PREFIX-MONTH-YEAR.
     */
    private String getThrottleToken()
    {
        SimpleDateFormat tokenFormat = new SimpleDateFormat("MMMM-yyyy");
        Calendar cal = Calendar.getInstance();
        return THROTTLE_TOKEN_PREFIX + "-" + tokenFormat.format(cal.getTime());
    }

    /**
     * Convert a dom clock value to days.
     * @param domclk The dom clock value.
     * @return The number of days.
     */
    private long domToDays(long domclk)
    {
        return (TimeUnits.DOM.asUTC(domclk) / 10) / NANOS_PER_DAY;
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
     * Development aid.
     */
    public static void main(String[] args)
    {
        Map<DOMChannelInfo, Long> domRecords = new HashMap<DOMChannelInfo, Long>(10);
        domRecords.put(new DOMChannelInfo(Long.toHexString(91234988934L), 0,1,'A'),
                TimeUnits.DOM.maxValue());
        domRecords.put(new DOMChannelInfo(Long.toHexString(2345232452345L), 0,1,'B'),
                TimeUnits.DOM.maxValue());
        domRecords.put(new DOMChannelInfo(Long.toHexString(56873452362L), 0,2,'A'),
                TimeUnits.DOM.maxValue());
        domRecords.put(new DOMChannelInfo(Long.toHexString(89743525L), 0,2,'B'),
                TimeUnits.DOM.maxValue());
        domRecords.put(new DOMChannelInfo(Long.toHexString(200856624542L), 4,1,'A'),
                TimeUnits.DOM.maxValue());
        domRecords.put(new DOMChannelInfo(Long.toHexString(54373473547L), 4,1,'A'),
                TimeUnits.DOM.maxValue());
        domRecords.put(new DOMChannelInfo(Long.toHexString(67342347547L), 5,5,'A'),
                TimeUnits.DOM.maxValue());
        domRecords.put(new DOMChannelInfo(Long.toHexString(256326546245L), 5,7,'B'),
                TimeUnits.DOM.maxValue());
        domRecords.put(new DOMChannelInfo(Long.toHexString(45235624574L), 6,1,'B'),
                TimeUnits.DOM.maxValue());
        domRecords.put(new DOMChannelInfo(Long.toHexString(845675624724L), 6,3,'A'),
                TimeUnits.DOM.maxValue());
        domRecords.put(new DOMChannelInfo(Long.toHexString(56425654645362L), 6,4,'B'),
                TimeUnits.DOM.maxValue());
        domRecords.put(new DOMChannelInfo(Long.toHexString(34574327234852L), 7,1,'A'),
                TimeUnits.DOM.maxValue());

        DOMClockRolloverAlerter example = new DOMClockRolloverAlerter();

        System.out.println("## WARNING ALERT ###");
        dump(example.buildWarningAlert(domRecords));
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println("## CRITICAL ALERT ###");
        dump(example.buildCriticalAlert(domRecords));

    }

    private static void dump(Map<String, Object> alertData)
    {
        Map<String, Object> dbnotify =
                (Map<String, Object>) alertData.get("dbnotify");

        System.out.println("condition:   " + alertData.get("condition"));
        System.out.println("desc:   " + alertData.get("desc"));

        System.out.println("receiver:   " + dbnotify.get("receiver"));
        System.out.println("subject:   " + dbnotify.get("subject"));
        System.out.println("body:");
        System.out.println(dbnotify.get("body"));
        System.out.println("throttleToken:   " + dbnotify.get("throttleToken"));
    }


}

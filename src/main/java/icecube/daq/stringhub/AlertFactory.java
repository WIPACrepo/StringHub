package icecube.daq.stringhub;

import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.juggler.alert.Alerter;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides methods for constructing and sending alert objects.
 *
 * If no email address is configured, alerts will be sent without email
 * section.
 *
 * <p>
 * Required Configuration:
 * <pre>
 *   icecube.daq.stringhub.alert-email = "foo@example.com"
 *
 *       The email addresses that will receive string hub email alerts.
 * </pre>
 */
public class AlertFactory
{

    /** An email address that will receive email alerts. */
    public static final String ALERT_EMAIL =
            System.getProperty("icecube.daq.stringhub.alert-email", "");

    /**
     * Sends an email alert
     * @param alertQueue The alert queue.
     * @param content The content of the alert, generated from
     *                the constructEmailAlert() method;
     * @throws AlertException An Error occured sending the alert.
     */
    public static void sendEmailAlert(final AlertQueue alertQueue,
                                      final Map<String, Object> content)
            throws AlertException
    {
        alertQueue.push("alert", Alerter.Priority.EMAIL, content);
    }

    /**
     * Construct an object map corresponding to "value" entry of a live
     * user email alert JSON struct.
     * <p>
     * <pre>
     * {"service"  : "xyzzy",
     *  "varname"  : "alert",
     *  "t"        : "2012-01-01 21:56:22.891184",
     *  "prio"     : 1,
     *  "value"    : { "condition" : "example",
     *                 "desc"      : "An example, for documentation",
     *                 "dbnotify"  : {"receiver"      : "someone@example.com",
     *                                "subject"       : "Example",
     *                                "body"          : "etc., etc, etc.",
     *                                "throttleToken" : "xyzzy"}
     *                 "vars"      : { "a" : 1.064,
     *                                 "b" : 0.178,
     *                                 "c" : 5.990,
     *                                 "d" : 8 }
     *              }
     * }
     * </pre>
     * @param condition The condition.
     * @param description A short description of the condition.
     * @param emailSubject Subject of email.
     * @param emailBody Body of email.
     * @param throttleToken Optional. Live suppresses multiple emails with the
     *                      same token.
     * @param vars Optional. Data variable to include in the alert.
     * @return A map of elements assignable to the "value" key of a user alert
     *         structure;
     */
    public static Map<String, Object> constructEmailAlert(final String condition,
                                             final String description,
                                             final String emailSubject,
                                             final String emailBody,
                                             final String throttleToken,
                                             final Map<String, Object> vars)
    {
        final Map<String, Object> valueMap = new HashMap<String, Object>();
        valueMap.put("condition", condition);
        valueMap.put("desc", description);

        // email section requires a configured recipient
        if(ALERT_EMAIL != null && !ALERT_EMAIL.equals(""))
        {
            Map<String, String> emailData = new HashMap<String, String>();
            emailData.put("receiver" , ALERT_EMAIL);
            emailData.put("subject", emailSubject);
            emailData.put("body", emailBody);
            if(throttleToken != null)
            {
                emailData.put("throttleToken", throttleToken);
            }
            valueMap.put("dbnotify", emailData);
        }

        // optional variable
        if(vars != null)
        {
            valueMap.put("vars", vars);
        }

        return valueMap;
    }


}

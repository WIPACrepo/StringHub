package icecube.daq.util;

import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.impl.UTCTime;
import icecube.daq.util.LocatePDAQ;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.apache.log4j.varia.NullAppender;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

class MockAlerter
    implements Alerter
{
    private Alerter.Priority expPrio;
    private String expCond;
    private Map<String, Object> expVars;

    public MockAlerter()
    {
    }

    private static void addErrors(StringBuilder buf, String name,
                                  List<String> vars)
    {
        Collections.sort(vars);

        String front;
        if (buf.length() == 0) {
            front = "Found ";
        } else {
            front = "; ";
        }
        buf.append(front).append(name).append(" variable");
        if (vars.size() > 1) {
            buf.append("s");
        }

        boolean first = true;
        for (String key : vars) {
            if (first) {
                buf.append(": ").append(key);
                first = false;
            } else {
                buf.append(", ").append(key);
            }
        }
    }

    @Override
    public void close()
    {
        // do nothing
    }

    @Override
    public String getService()
    {
        return DEFAULT_SERVICE;
    }

    @Override
    public boolean isActive()
    {
        return true;
    }

    public void send(String varname, Alerter.Priority priority,
                     Map<String, Object> vars)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    public void send(String varname, Alerter.Priority priority,
                     Calendar dateTime, Map<String, Object> vars)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param utcTime DAQ time
     * @param values map of names to values
     */
    public void send(String varname, Priority priority, IUTCTime utcTime,
              Map<String, Object> values)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    public void sendAlert(Alerter.Priority priority, String condition,
                          Map<String, Object> vars)
        throws AlertException
    {
        sendAlert(priority, condition, null, vars);
    }

    public void sendAlert(Alerter.Priority priority, String condition,
                          String notify, Map<String, Object> vars)
        throws AlertException
    {
        sendAlert((Calendar) null, priority, condition, notify, vars);
    }

    public void sendAlert(Calendar dateTime, Alerter.Priority priority,
                          String condition, String notify,
                          Map<String, Object> vars)
        throws AlertException
    {
        if (priority != expPrio) {
            throw new Error("Expected alert priority " + expPrio + ", got " +
                            priority);
        }

        if (!condition.equals(expCond)) {
            throw new Error("Expected alert condition \"" + expCond +
                            "\", got \"" + condition);
        }

        if ((vars == null || vars.size() == 0) &&
            (expVars != null && expVars.size() > 0))
        {
            throw new Error("Expected " + expVars.size() +
                            " variables, not 0");
        } else if ((vars != null && vars.size() > 0) &&
            (expVars == null || expVars.size() == 0))
        {
            throw new Error("Didn't expect any variables, but found " +
                            vars.size());
        } else if (vars != null && vars.size() > 0 && expVars != null &&
                   expVars.size() > 0)
        {
            List<String> unexpected = new ArrayList<String>();
            List<String> extra = new ArrayList<String>(expVars.keySet());
            List<String> badVal = new ArrayList<String>();

            for (String key : vars.keySet()) {
                if (!expVars.containsKey(key)) {
                    unexpected.add(key);
                } else {
                    extra.remove(key);

                    Object expVal = expVars.get(key);
                    Object actVal = vars.get(key);

                    if (!expVal.equals(actVal)) {
                        badVal.add(key);
                    }
                }
            }

            StringBuilder buf = new StringBuilder();
            if (unexpected.size() > 0) {
                addErrors(buf, "unexpected", unexpected);
            }

            if (extra.size() > 0) {
                addErrors(buf, "extra", extra);
            }

            if (badVal.size() > 0) {
                addErrors(buf, "bad", badVal);
            }

            if (buf.length() > 0) {
                throw new Error(buf.toString());
            }
        }
    }

    public void sendAlert(IUTCTime utcTime, Alerter.Priority priority,
                          String condition, String notify,
                          Map<String, Object> vars)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void sendObject(Object obj)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void setAddress(String host, int port)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    void setExpected(Alerter.Priority priority, String condition,
                     Map<String, Object> vars)
    {
        expPrio = priority;
        expCond = condition;
        expVars = vars;
    }
}

public class StringHubAlertTest
{
    @BeforeClass
    public static void setupLogging()
    {
        // exercise logging calls, but output to nowhere
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(new NullAppender());
        Logger.getRootLogger().setLevel(Level.ALL);
    }

    @AfterClass
    public static void tearDownLogging()
    {
        BasicConfigurator.resetConfiguration();
    }


    @Before
    public void setUp()
    {
        // set the config directory so UTCTime.toDateString() works
        File configDir = new File(getClass().getResource("/config").getPath());
        if (!configDir.exists()) {
            throw new IllegalArgumentException("Cannot find config" +
                                               " directory under " +
                                               getClass().getResource("/"));
        }

        System.setProperty(LocatePDAQ.CONFIG_DIR_PROPERTY,
                           configDir.getAbsolutePath());
    }

    @After
    public void tearDown()
    {
        System.clearProperty(LocatePDAQ.CONFIG_DIR_PROPERTY);
    }

    @Test
    public void testAlert()
        throws Exception
    {
        final String condition = "Test DOM alert";
        final int card = 1;
        final int pair = 23;
        final char dom = 'A';
        final String mbid = "123456789ABC";
        final String name = "TestDOM";
        final int string = 12;
        final int position = 34;
        final int runNumber = 56789;

        HashMap<String, Object> vars = new HashMap<String, Object>();
        vars.put("card", Integer.valueOf(card));
        vars.put("pair", Integer.valueOf(pair));
        vars.put("dom", dom);
        vars.put("mbid", mbid);
        vars.put("name", name);
        vars.put("string", string);
        vars.put("position", position);

        MockAlerter alerter = new MockAlerter();
        alerter.setExpected(StringHubAlert.DEFAULT_PRIORITY, condition, vars);

        AlertQueue aq = new AlertQueue(alerter);
        StringHubAlert.sendDOMAlert(aq, StringHubAlert.DEFAULT_PRIORITY,
                                    condition, card, pair, dom, mbid, name,
                                    string, position,
                                    StringHubAlert.NO_RUNNUMBER,
                                    StringHubAlert.NO_UTCTIME);
        aq.stopAndWait();
    }

    @Test
    public void testAlertPlusTime()
        throws Exception
    {
        final String condition = "Test DOM alert";
        final int card = 1;
        final int pair = 23;
        final char dom = 'A';
        final String mbid = "123456789ABC";
        final String name = "TestDOM";
        final int string = 12;
        final int position = 34;
        final int runNumber = 123456;
        final long utcTime = 123456789L;

        HashMap<String, Object> vars = new HashMap<String, Object>();
        vars.put("card", Integer.valueOf(card));
        vars.put("pair", Integer.valueOf(pair));
        vars.put("dom", dom);
        vars.put("mbid", mbid);
        vars.put("name", name);
        vars.put("string", string);
        vars.put("position", position);
        vars.put("runNumber", runNumber);
        vars.put("exact-time", UTCTime.toDateString(utcTime));

        MockAlerter alerter = new MockAlerter();
        alerter.setExpected(Alerter.Priority.SCP, condition, vars);

        AlertQueue aq = new AlertQueue(alerter);
        StringHubAlert.sendDOMAlert(aq, StringHubAlert.DEFAULT_PRIORITY,
                                    condition, card, pair, dom, mbid, name,
                                    string, position, runNumber,
                                    utcTime);
        aq.stopAndWait();
    }

    @Test
    public void testAlertNegativeTime()
        throws Exception
    {
        final String condition = "Test DOM alert";
        final int card = 1;
        final int pair = 23;
        final char dom = 'A';
        final String mbid = "123456789ABC";
        final String name = "TestDOM";
        final int string = 12;
        final int position = 34;
        final int runNumber = 123456;
        final long utcTime = -1;

        HashMap<String, Object> vars = new HashMap<String, Object>();
        vars.put("card", Integer.valueOf(card));
        vars.put("pair", Integer.valueOf(pair));
        vars.put("dom", dom);
        vars.put("mbid", mbid);
        vars.put("name", name);
        vars.put("string", string);
        vars.put("position", position);
        vars.put("runNumber", runNumber);

        MockAlerter alerter = new MockAlerter();
        alerter.setExpected(Alerter.Priority.SCP, condition, vars);

        AlertQueue aq = new AlertQueue(alerter);
        StringHubAlert.sendDOMAlert(aq, StringHubAlert.DEFAULT_PRIORITY,
                                    condition, card, pair, dom, mbid, name,
                                    string, position, runNumber,
                                    utcTime);
        aq.stopAndWait();
    }

    @Test
    public void testAlertPermutations()
        throws Exception
    {
        final String condition = "Test DOM alert";
        final int card = 1;
        final int pair = 23;
        final char dom = 'A';
        final String mbid = "123456789ABC";
        final String name = "TestDOM";
        final int string = 12;
        final int position = 34;
        final int runNumber = 56789;
        final long utcTime = 123456789L;

        for (int i = 0; i < 8; i++) {
            Alerter.Priority thisPrio = StringHubAlert.DEFAULT_PRIORITY;
            int thisRunNum = StringHubAlert.NO_RUNNUMBER;
            long thisTime = StringHubAlert.NO_UTCTIME;

            if ((i & 1) == 1) {
                thisPrio = Alerter.Priority.ITS;
            }

            HashMap<String, Object> vars = new HashMap<String, Object>();
            vars.put("card", Integer.valueOf(card));
            vars.put("pair", Integer.valueOf(pair));
            vars.put("dom", dom);
            vars.put("mbid", mbid);
            vars.put("name", name);
            vars.put("string", string);
            vars.put("position", position);
            if ((i & 2) == 2) {
                thisRunNum = runNumber;
                vars.put("runNumber", runNumber);
            }
            if ((i & 4) == 4) {
                thisTime = utcTime;
                vars.put("exact-time", UTCTime.toDateString(utcTime));
            }

            MockAlerter alerter = new MockAlerter();
            alerter.setExpected(thisPrio, condition, vars);

            AlertQueue aq = new AlertQueue(alerter);
            StringHubAlert.sendDOMAlert(aq, thisPrio, condition, card, pair,
                                        dom, mbid, name, string, position,
                                        thisRunNum, thisTime);
            aq.stopAndWait();
        }
    }
}

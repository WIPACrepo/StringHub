package icecube.daq.stringhub.test;

import icecube.daq.common.IDAQAppender;

import java.util.ArrayList;

import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Mock log4j appender.
 */
public class MockAppender
    implements IDAQAppender
{
    /** minimum level of log messages which will be print. */
    private Level minLevel;
    /** <tt>true</tt> if messages should be printed as well as cached. */
    private boolean verbose;
    /** <tt>true</tt> if messages are not kept. */
    private boolean flushMsgs;

    private ArrayList<LoggingEvent> eventList;

    /**
     * Create a MockAppender which ignores everything below the WARN level.
     */
    public MockAppender()
    {
        this(Level.WARN);
    }

    /**
     * Create a MockAppender which ignores everything
     * below the specified level.
     *
     * @param minLevel minimum level
     */
    public MockAppender(Level minLevel)
    {
        this.minLevel = minLevel;
        eventList = new ArrayList<LoggingEvent>();
    }

    /**
     * Unimplemented.
     *
     * @param x0 ???
     */
    public void addFilter(Filter x0)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Clear the cached logging events.
     */
    public void clear()
    {
        eventList.clear();
    }

    /**
     * Unimplemented.
     */
    public void clearFilters()
    {
        throw new Error("Unimplemented");
    }

    /**
     * Nothing needs to be done here.
     */
    public void close()
    {
        // don't need to do anything
    }

    /**
     * Handle a logging event.
     *
     * @param evt logging event
     */
    public void doAppend(LoggingEvent evt)
    {
        if (evt.getLevel().toInt() >= minLevel.toInt()) {
            if (!flushMsgs) {
                eventList.add(evt);
            }

            if (verbose) {
                dumpEvent(evt);
            }
        }
    }

    /**
     * Dump a logging event to System.out
     *
     * @param evt logging event
     */
    private void dumpEvent(LoggingEvent evt)
    {
        LocationInfo loc = evt.getLocationInformation();

        System.out.println(evt.getLoggerName() + " " + evt.getLevel() +
                           " [" + loc.fullInfo + "] " +
                           evt.getMessage());

        String[] stack = evt.getThrowableStrRep();
        for (int i = 0; stack != null && i < stack.length; i++) {
            System.out.println("> " + stack[i]);
        }
    }

    /**
     * Unimplemented.
     *
     * @return ???
     */
    public ErrorHandler getErrorHandler()
    {
        throw new Error("Unimplemented");
    }

    private LoggingEvent getEvent(int idx)
    {
        if (idx < 0 || idx > eventList.size()) {
            throw new IllegalArgumentException("Bad index " + idx);
        }

        return eventList.get(idx);
    }

    /**
     * Unimplemented.
     *
     * @return ???
     */
    public Filter getFilter()
    {
        throw new Error("Unimplemented");
    }

    /**
     * Unimplemented.
     *
     * @return ???
     */
    public Layout getLayout()
    {
        throw new Error("Unimplemented");
    }

    /**
     * Get logging level.
     *
     * @return logging level
     */
    public Level getLevel()
    {
        return minLevel;
    }

    public Object getMessage(int idx)
    {
        return getEvent(idx).getMessage();
    }

    /**
     * Unimplemented.
     *
     * @return ???
     */
    public String getName()
    {
        throw new Error("Unimplemented");
    }

    public int getNumberOfMessages()
    {
        return eventList.size();
    }

    /**
     * Is this appender sending log messages to the specified host and port.
     *
     * @param host host name/IP address
     * @param port port number
     *
     * @return <tt>true</tt> if this appender uses the host:port
     */
    public boolean isConnected(String host, int port)
    {
        return true;
    }

    /**
     * Unimplemented.
     *
     * @return ???
     */
    public boolean requiresLayout()
    {
        throw new Error("Unimplemented");
    }

    /**
     * Unimplemented.
     *
     * @param x0 ???
     */
    public void setErrorHandler(ErrorHandler x0)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Should log messages be flushed?
     *
     * @param val <tt>false</tt> if log messages should be saved
     */
    public void setFlushMessages(boolean val)
    {
        flushMsgs = val;
    }

    /**
     * Unimplemented.
     *
     * @param x0 ???
     */
    public void setLayout(Layout x0)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Set logging level.
     *
     * @param lvl logging level
     */
    public MockAppender setLevel(Level lvl)
    {
        minLevel = lvl;

        return this;
    }

    /**
     * Unimplemented.
     *
     * @param s0 ???
     */
    public void setName(String s0)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Set verbosity.
     *
     * @param val <tt>true</tt> if log messages should be printed
     */
    public MockAppender setVerbose(boolean val)
    {
        verbose = val;

        return this;
    }
}

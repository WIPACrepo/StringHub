package icecube.daq.sender.test;

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
    implements Appender
{
    /** minimum level of log messages which will be print. */
    private Level minLevel;

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
            LocationInfo loc = evt.getLocationInformation();

            System.out.println(evt.getLoggerName() + " " + evt.getLevel() +
                               " [" + loc.fullInfo + "] " + evt.getMessage());

            String[] stack = evt.getThrowableStrRep();
            for (int i = 0; stack != null && i < stack.length; i++) {
                System.out.println("> " + stack[i]);
            }
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
     * Unimplemented.
     *
     * @return ???
     */
    public String getName()
    {
        throw new Error("Unimplemented");
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
     * Unimplemented.
     *
     * @param x0 ???
     */
    public void setLayout(Layout x0)
    {
        throw new Error("Unimplemented");
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
}

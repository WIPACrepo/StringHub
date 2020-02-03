package icecube.daq.performance.diagnostic;



import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Defines types that provide diagnostic trace content.  Content should
 * provide a fixed-width formatted header and content suitable to be
 * appended to a single trace line resulting in a fixed with trace.
 *
 */
public interface Content
{

    /**
     * Supports sharing per-line data across multiple content
     * instances.
     */
    public interface FlyWeight
    {
        public void beforeContent();
    }

    /**
     * Append the header field(s) to the buffer.
     */
    public void header(StringBuilder sb);

    /**
     * Append the content field(s) to the buffer.
     */
    public void content(StringBuilder sb);


    /** Conversion constants. */
    static final int BYTES_PER_MB = 1024*1024;
    static final long MILLIS_PER_SECOND = 1000;


    /**
     * Supports pre-pending header lines with a comment character.
     */
    public class CommentContent implements Content
    {

        final String header;
        final String content;

        public CommentContent()
        {
            this("#");
        }

        public CommentContent(final String comment)
        {
            this.header = comment;
            this.content = String.format("%"+comment.length()+"s", "");
        }

        @Override
        public void header(StringBuilder sb)
        {
            sb.append(header);
        }

        @Override
        public void content(StringBuilder sb)
        {
            sb.append(content);
        }

    }


    /**
     * A static column to provide a visual divider.
     */
    public class DividerContent implements Content
    {

        final String divider;

        public DividerContent()
        {
            this("###");
        }

        public DividerContent(final String divider)
        {
            this.divider = divider + "   ";
        }

        @Override
        public void header(StringBuilder sb)
        {
            sb.append(divider);
        }

        @Override
        public void content(StringBuilder sb)
        {
            sb.append(divider);
        }

    }


    public class JoinedContent implements Content
    {
        private final Content[] content;


        public JoinedContent(final Content... content)
        {
            this.content = content;
        }

        @Override
        public void header(final StringBuilder sb)
        {
            for (int i = 0; i < content.length; i++)
            {
                content[i].header(sb);

            }
        }

        @Override
        public void content(final StringBuilder sb)
        {
            for (int i = 0; i < content.length; i++)
            {
                content[i].content(sb);

            }
        }
    }

    /**
     * Bracket sub-content with a named grouping
     */
    public class GroupedContent implements Content
    {
        private final String openHeader;
        private final String closeHeader;
        private final String openContent;
        private final String closeContent;
        private final Content content;

        public GroupedContent(final String name,
                              final Content content)
        {
            this.openHeader = name + "  [ ";
            this.closeHeader = "]   ";
            String openFormat = "%" + name.length() +
                    "s%" + (openHeader.length()-name.length()) + "s";
            String closeFormat = "%-" + closeHeader.length() + "s";
            this.openContent = String.format(openFormat, "", "[ ");
            this.closeContent = String.format(closeFormat, "]");
            this.content = content;
        }

        @Override
        public void header(StringBuilder sb)
        {
            sb.append(openHeader);
            content.header(sb);
            sb.append(closeHeader);
        }

        @Override
        public void content(StringBuilder sb)
        {
            sb.append(openContent);
            content.content(sb);
            sb.append(closeContent);
        }

    }


    /**
     * Display a timestamp as "YYYY-MM-dd HH:mm:ss.S"
     */
    public class TimeContent implements Content
    {
        final DateFormat dateFormat =
                new SimpleDateFormat("YYYY-MM-dd HH:mm:ss.S");

        final String header;

        public TimeContent()
        {
            header = String.format("%-25s", "time");
        }

        @Override
        public void header(StringBuilder sb)
        {
            sb.append(header);
        }

        @Override
        public void content(StringBuilder sb)
        {
            sb.append(String.format("%-25s", dateFormat.format(new Date())));
        }

    }


    /**
     *  Display age from construction or other point-in-time as
     *  N-sec, N-min or N-hour
     */
    public class AgeContent implements Content
    {
        private final long epoch;

        private final String header;

        public AgeContent()
        {
            this(System.nanoTime());
        }

        public AgeContent(long epoch)
        {
            this.epoch = epoch;
            this.header = String.format("%-6s","age");
        }

        @Override
        public void header(StringBuilder sb)
        {
            sb.append(header);
        }

        @Override
        public void content(StringBuilder sb)
        {
            final String field;
            final String unit;
            long secondsAge = (System.nanoTime() - epoch)/1000000000;
            if(secondsAge < 60)
            {
                field = Long.toString(secondsAge);
                unit = "s";
            }
            else  if (secondsAge < 3600)
            {
                field = String.format("%1.1f", (secondsAge/60f));
                unit = "m";
            }
            else
            {
                field = String.format("%1.1f", (secondsAge/3600f));
                unit = "h";
            }

            sb.append(String.format("%-6s", (field + unit)));
        }

    }


    public class HeapMemoryContent implements Content
    {

        private final String header;

        public HeapMemoryContent()
        {
            header = String.format("%-8s", "heapmb");
        }

        @Override
        public void header(StringBuilder sb)
        {
            sb.append(header);
        }

        @Override
        public void content(StringBuilder sb)
        {
            sb.append(String.format("%-8d", getHeapUsageBytes()/ BYTES_PER_MB));
        }

        private static long getHeapUsageBytes() {
            MemoryUsage mem =
                    ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            return mem.getUsed();
        }

    }


    public class GCContent implements Content
    {

        private final String header;

        public GCContent()
        {
            header = String.format("%-6s %-6s %-6s %-6s",
                    "ygcc", "ygct", "ogcc", "ogct");
        }

        @Override
        public void header(StringBuilder sb)
        {
            sb.append(header);
        }

        @Override
        public void content(StringBuilder sb)
        {
            List<long[]> gcCountAndTimes = getGCCountAndTimes();
            long[] young = gcCountAndTimes.get(0);
            long[] old = gcCountAndTimes.get(1);
            sb.append(String.format("%-6d %-6d %-6d %-6d",
                    young[0], young[1]/ MILLIS_PER_SECOND,
                    old[0], old[1]/ MILLIS_PER_SECOND));
        }

        private static List<long[]> getGCCountAndTimes() {
            List<long[]> data = new ArrayList<long[]>(2);
            for (GarbageCollectorMXBean garbageCollectorMXBean :
                    ManagementFactory.getGarbageCollectorMXBeans()) {
                long collectionTime =
                        garbageCollectorMXBean.getCollectionTime();
                long count = garbageCollectorMXBean.getCollectionCount();
                data.add(new long[]{count, collectionTime});
            }
            return data;
        }

    }


}

package icecube.daq.stringhub;

import icecube.daq.bindery.SecondaryStreamConsumer;
import icecube.daq.io.OutputChannel;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * A non-standard diagnostic capture and persist utility for capturing
 * tcal payloads from the SecondaryStreamConsumer.
 *
 *<p>
 * Three properties control tcal capture:
 *<pre>
 *
 *    icecube.daq.stringhub.TimeCalibrationCapture.enable = [false]
 *
 *       Must be set to true to activate capture.
 *
 *    icecube.daq.stringhub.TimeCalibrationCapture.storage-directory
 *
 *       If enable, required to be set to a writable or creatable directory
 *       location. Capture files will appear here. Multiple run capture
 *       files will be managed by rotation of current.dat to last.dat.
 *
 *    icecube.daq.stringhub.TimeCalibrationCapture.mbid-filter = [none]
 *
 *       A comma-separated list of mbids to capture tcals for. Supports
 *       any format accepted by Long.decode(). The values "none" and
 *       "all" are also accepted.
 *
 *</pre>
 *
 * <p>
 * payloads are assumed to be in the secondary stream tcal payload
 * format (i.e. type 4) with a header format:
 *
 * <pre>
 * int   length
 * int   format-id (4)
 * long  utc
 * long  mbid
 * </pre>
 *
 */
public class TimeCalibrationCaptureSubsystem
{
    private static Logger logger =
            Logger.getLogger(TimeCalibrationCaptureSubsystem.class);


    public static String ENABLE_PROPERTY =
            "icecube.daq.stringhub.TimeCalibrationCapture.enable";

    public static String STORAGE_PROPERTY =
            "icecube.daq.stringhub.TimeCalibrationCapture.storage-directory";

    public static String FILTER_PROPERTY =
            "icecube.daq.stringhub.TimeCalibrationCapture.mbid-filter";


    /**
     * Activate TCal capture per configuration.
     *
     * @param tcalStream The tcal secondary stream;
     * @throws IOException  An error was encountered activating capture;
     */
    public static void activate(final SecondaryStreamConsumer tcalStream)
    {
        boolean enabled =
                Boolean.getBoolean(TimeCalibrationCaptureSubsystem.ENABLE_PROPERTY);

        String storageDir  =
                System.getProperty(TimeCalibrationCaptureSubsystem.STORAGE_PROPERTY);

        String filterConfig  =
                System.getProperty(TimeCalibrationCaptureSubsystem.FILTER_PROPERTY);

        if(enabled)
        {
            if(storageDir == null)
            {
                logger.error(TimeCalibrationCaptureSubsystem.STORAGE_PROPERTY +
                        " was not defined, aborting tcal capture.");
            }
            else
            {

                final Filter filter = makeFilter(filterConfig);
                final TimeCalibrationCapture tcalCapture =
                        new TimeCalibrationCapture(storageDir, filter);

                try
                {
                    tcalCapture.manageFiles();
                    tcalCapture.attach(tcalStream);
                }
                catch (IOException ioe)
                {
                    // NOTE: failure does not impinge the run.
                    logger.error("TCal capture activation failed", ioe);
                }
            }
        }

    }

    private static Filter makeFilter(String configString)
    {
        if(configString.equalsIgnoreCase("none"))
        {
            return new Filter.NoneFilter();
        }
        else if(configString.equalsIgnoreCase("all"))
        {
            return new Filter.AllFilter();
        }
        else
        {
            String[] mbids = configString.split(",");
            long[] values = new long[mbids.length];
            for (int i = 0; i < values.length; i++)
            {
                values[i] = Long.decode(mbids[i]);
            }
            return new Filter.MbidFilter(values);
        }
    }

    /**
     * Implements capture.
     */
    static class TimeCalibrationCapture
    {
        private final Path storageDirectory;
        private final Path current;
        private final Path previous;
        private final Filter filter;


        public TimeCalibrationCapture(final String storageDirectory,
                                      final Filter filter)
        {
            this(new File(storageDirectory).toPath(), filter);
        }


        public TimeCalibrationCapture(final Path storageDirectory,
                                      final Filter filter)
        {
            this.storageDirectory = storageDirectory;
            this.current = storageDirectory.resolve("current.dat");
            this.previous = storageDirectory.resolve("last.dat");
            this.filter = filter;
        }

        public void attach(SecondaryStreamConsumer stream) throws IOException
        {
            FilePersistence persistence = new FilePersistence(current, filter);
            stream.setDebugChannel(new CaptureAdapter(persistence));
        }

        public void manageFiles() throws IOException
        {
            if(!Files.exists(storageDirectory))
            {
                Files.createDirectory(storageDirectory);
            }

            if(Files.exists(current))
            {
                Files.move(current, previous, StandardCopyOption.REPLACE_EXISTING);
            }
        }

    }


    /**
     * Implements storing a buffer stream to a file.
     */
    private static class FilePersistence implements OutputChannel
    {

        private final Filter filter;
        private final FileChannel channel;

        private boolean error;

        private FilePersistence(final Path file, final Filter filter)
                throws IOException
        {
            this.filter = filter;
            channel = FileChannel.open(file,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }

        @Override
        public void receiveByteBuffer(final ByteBuffer buf)
        {
            if(!error)
            {
                try
                {
                    if(filter.accept(buf))
                    {
                        channel.write(buf);
                    }
                }
                catch (IOException e)
                {
                    logger.error(e);
                    error=true;
                }
            }
        }

        @Override
        public void sendLastAndStop()
        {
            try
            {
                channel.close();
            }
            catch (IOException e)
            {
                logger.error(e);
            }
        }

    }


    /**
     * Defines a filtering interface for selecting individual TCal payloads.
     */
    private static interface Filter
    {
        public boolean accept(final ByteBuffer buf) throws IOException;


        static class NoneFilter implements Filter
        {
            @Override
            public boolean accept(final ByteBuffer buf) throws IOException
            {
                return false;
            }
        }

        static class AllFilter implements Filter
        {
            @Override
            public boolean accept(final ByteBuffer buf) throws IOException
            {
                return true;
            }
        }

        /**
         * Compares the mainboard id field of the secondary stream payload
         * against a whitelist of mbid values.
         */
        static class MbidFilter implements Filter
        {
            private final long[] mbids;

            private MbidFilter(final long[] mbids)
            {
                this.mbids = mbids;
            }

            public boolean accept(final ByteBuffer buf) throws IOException
            {
                long mbid = buf.getLong(16);
                for (int i = 0; i < mbids.length; i++)
                {
                    if(mbid == mbids[i])
                    {
                        return true;
                    }
                }
                return false;
            }
        }

    }


    /**
     * Captures buffers from SecondaryStreamConsumer via the
     * setDebugChannel(WritableByteChannel ch) method.
     */
    private static class CaptureAdapter implements WritableByteChannel
    {
        private final OutputChannel sink;
        boolean open;

        private CaptureAdapter(final OutputChannel sink)
        {
            this.sink = sink;
            open = true;
        }

        @Override
        public int write(final ByteBuffer src) throws IOException
        {
            int remaining = src.remaining();
            sink.receiveByteBuffer(src);
            return remaining;
        }

        @Override
        public boolean isOpen()
        {
            return open;
        }

        @Override
        public void close() throws IOException
        {
            sink.sendLastAndStop();
            open = false;
        }

    }


}

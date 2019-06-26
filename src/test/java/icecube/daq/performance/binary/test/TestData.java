package icecube.daq.performance.binary.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;

/**
 * Production data sets for test use.
 */
public enum TestData
{
    /** Delta-compressed hit records taken from run 125659. */
    DELTA_COMPRESSED("125659-HitSpool.dat.gz");

    /** The resource name.*/
    private final String fileName;

    private TestData(final String fileName)
    {
        this.fileName = fileName;
    }

    /**
     * Load the data set into a stream.
     * @return The data content as a stream.
     * @throws IOException
     */
    public InputStream getStream() throws IOException
    {
        InputStream stream = this.getClass().getResourceAsStream(fileName);
        if(fileName.endsWith(".gz"))
        {
            return new GZIPInputStream(stream);
        }
        else
        {
            return stream;
        }
    }

    /**
     * Load the data into a ByteBuffer.
     *
     * @return A buffer containing the data.
     * @throws IOException
     */
    public ByteBuffer toByteBuffer() throws IOException
    {
        InputStream stream = getStream();
        ByteArrayOutputStream accumulator = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while( (read = stream.read(buffer)) > 0)
        {
            accumulator.write(buffer, 0, read);
        }

        return ByteBuffer.wrap(accumulator.toByteArray());
    }

}

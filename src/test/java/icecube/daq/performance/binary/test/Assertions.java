package icecube.daq.performance.binary.test;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.record.RecordReader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Utility methods to make assertions about RecordBuffers.
 */
public class Assertions
{


    public static void assertSame(String msg, RecordBuffer expected, RecordBuffer actual)
    {
        assertEquals(msg, expected.getLength(), actual.getLength());
        for (int i = 0; i<expected.getLength(); i++)
        {
            assertEquals(msg, expected.getByte(i), actual.getByte(i));
        }

        // belt + suspenders
        assertArrayEquals(msg, expected.getBytes(0, expected.getLength()),
                actual.getBytes(0, actual.getLength()));
    }

    /**
     * check that a record buffer contains records with exactly
     * the specified values in the specified order.
     */
    public static void assertContainsExactly(String msg,
                                              RecordReader recordReader,
                                              RecordReader.LongField orderingField,
                                              RecordBuffer rb,
                                              long[] values)
    {
        int recordNumber = 0;
        for ( RecordBuffer record : rb.eachRecord(recordReader))
        {
            long val = orderingField.value(record, 0);
            assertEquals(msg, values[recordNumber], val);
            recordNumber++;
        }
    }

    /**
     * check that a value list contains exactly
     * the specified values in the specified order.
     */
    public static void assertContainsExactly(String msg,
                                              List<Long> list,
                                              long[] values)
    {
        int recordNumber = 0;
        for ( Long val : list)
        {
            assertEquals(msg, values[recordNumber], val.longValue());
            recordNumber++;
        }
    }

    /**
     * check that a record buffer contains records with all specified
     * values but allowing for repeat values.
     */
    public static void assertContainsAtLeast(String msg,
                                              RecordReader recordReader,
                                              RecordReader.LongField orderingField,
                                              RecordBuffer rb,
                                              long[] values)
    {
        Set<Long> allowed = new HashSet<>(values.length);
        for (int i = 0; i < values.length; i++)
        {
            allowed.add(values[i]);
        }

        Set<Long> saw = new HashSet<>(values.length);
        for ( RecordBuffer record : rb.eachRecord(recordReader))
        {
            long value = orderingField.value(record, 0);
            assertTrue(msg, allowed.contains(value));
            saw.add(value);
        }

        for (int i = 0; i < values.length; i++)
        {
            assertTrue(msg, saw.contains(values[i]));
        }
    }

    /**
     * check that a value list contains all of the specified values,
     * allowing repeating instances.
     */
    public static void assertContainsAtLeast(String msg,
                                              List<Long> list,
                                              long[] values)
    {
        Set<Long> allowed = new HashSet<>(values.length);
        for (int i = 0; i < values.length; i++)
        {
            allowed.add(values[i]);
        }

        Set<Long> saw = new HashSet<>(values.length);
        for ( Long value : list)
        {
            assertTrue(msg, allowed.contains(value));
            saw.add(value);
        }

        for (int i = 0; i < values.length; i++)
        {
            assertTrue(msg, saw.contains(values[i]));
        }
    }

    /**
     * check that a record buffer contains records within the specified bounds.
     */
    public static void assertBounds(String msg,
                                     RecordReader recordReader,
                                     RecordReader.LongField orderingField,
                                     RecordBuffer rb,
                                     long from,
                                     long to)
    {

        for ( RecordBuffer record : rb.eachRecord(recordReader))
        {
            long value = orderingField.value(record, 0);
            assertTrue(msg, value>=from);
            assertTrue(msg, value<=to);
        }
    }

}

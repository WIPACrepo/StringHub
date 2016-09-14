package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.binary.store.impl.test.Mock;
import icecube.daq.performance.binary.test.RandomOrderedValueSequence;
import icecube.daq.performance.binary.test.RecordGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests RecordValidator.java
 */
@RunWith(Parameterized.class)
public class RecordValidatorTest
{
    @Parameterized.Parameter(0)
    public RecordGenerator generator;

    @Parameterized.Parameters(name = "RecordValidator[{0}]")
    public static List<Object[]> sizes()
    {
        List<Object[]> cases = new ArrayList<Object[]>(13);
        cases.add(new Object[]{new RecordGenerator.FixedLengthRecordProvider(1024)});
        cases.add(new Object[]{new RecordGenerator.RandomLengthRecordProvider(1024)});
        cases.add(new Object[]{new RecordGenerator.DAQRecordProvider(1024)});
        cases.add(new Object[]{new RecordGenerator.DAQRecordProvider(1024)});
        cases.add(new Object[]{new RecordGenerator.SmallRecordProvider()});
        return cases;
    }

    @Test
    public void testOrderDelegation() throws IOException
    {
        ///
        /// tests that method calls are delegated to the base store
        ///

        RandomOrderedValueSequence sequence = new RandomOrderedValueSequence();
        Mock.MockStore mock = new Mock.MockStore();
        RecordStore.OrderedWritable subject =
                RecordValidator.orderValidate(generator.orderingField(),
                        mock);

        // test delegation
        ByteBuffer record = generator.generate(sequence.next());
        String recordStr = record.toString();
        subject.available();
        subject.store(record);
        subject.extractRange(88, -72);
        subject.forEach(null, 123, 999);
        String[] expected = new String[]
                {
                        "available()",
                        "store("+recordStr+")",
                        "extractRange(" + 88 + ", " + -72 + ")",
                        "forEach(null, " + 123 + ", " + 999 + ")",
                };
        mock.assertCalls(expected);

    }

    @Test
    public void testOrderValidation() throws IOException
    {
        ///
        /// tests that out-of-order records are detected and
        //  cause an exception
        ///

        RandomOrderedValueSequence sequence = new RandomOrderedValueSequence();
        Mock.MockStore mock = new Mock.MockStore();
        RecordStore.OrderedWritable subject =
                RecordValidator.orderValidate(generator.orderingField(),
                        mock);

        subject.store(generator.generate(sequence.next()));
        subject.store(generator.generate(sequence.next()));
        subject.store(generator.generate(sequence.next()));
        subject.store(generator.generate(sequence.next()));
        long value = sequence.next();
        subject.store(generator.generate(value));
        subject.store(generator.generate(value));

        try
        {
            subject.store(generator.generate(value-1));
            fail("Out-of-order value accepted");
        }
        catch (IOException ioe)
        {
            String expected = "Invalid record order, last: " +
                    value + ", current " + (value-1);
            assertEquals(expected, ioe.getMessage());
        }

    }


    @Test
    public void testLengthDelegation() throws IOException
    {
        ///
        /// tests that method calls are delegated to the base store
        ///

        RandomOrderedValueSequence sequence = new RandomOrderedValueSequence();
        Mock.MockStore mock = new Mock.MockStore();
        RecordStore.OrderedWritable subject =
                RecordValidator.lengthValidate(generator.recordReader(),
                        mock);

        // test delegation
        ByteBuffer record = generator.generate(sequence.next());
        String recordStr = record.toString();
        subject.available();
        subject.store(record);
        subject.extractRange(88, -72);
        subject.forEach(null, 123, 999);
        String[] expected = new String[]
                {
                        "available()",
                        "store("+recordStr+")",
                        "extractRange(" + 88 + ", " + -72 + ")",
                        "forEach(null, " + 123 + ", " + 999 + ")",
                };
        mock.assertCalls(expected);

    }


    @Test
    public void testLengthValidation() throws IOException
    {
        ///
        /// tests that records with an incorrect length field are detected.
        ///

        RandomOrderedValueSequence sequence = new RandomOrderedValueSequence();
        Mock.MockStore mock = new Mock.MockStore();
        RecordStore.OrderedWritable subject =
                RecordValidator.lengthValidate(generator.recordReader(),
                        mock);


        subject.store(generator.generate(sequence.next()));
        subject.store(generator.generate(sequence.next()));
        subject.store(generator.generate(sequence.next()));
        subject.store(generator.generate(sequence.next()));

        ByteBuffer record = generator.generate(sequence.next());
        int size = record.limit();

        // too small
        try
        {
            record.limit(size -1);
            subject.store(record);
            fail("small length record accepted");
        }
        catch (IOException ioe)
        {
            String expected = "Invalid record length, says: "
                    + size + ", actual " + (size-1);
            assertEquals(expected, ioe.getMessage());
        }

        // too big
        try
        {
            ByteBuffer tooBig = ByteBuffer.allocate(size + 1);
            record.limit(size);

            tooBig.put(record);
            tooBig.put((byte) 1);
            tooBig.flip();
            subject.store(tooBig);
            fail("big length record accepted");
        }
        catch (IOException ioe)
        {
            String expected = "Invalid record length, says: "
                    + size + ", actual " + (size+1);
            assertEquals(expected, ioe.getMessage());
        }
    }

}

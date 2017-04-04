package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.store.RecordStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Adds record validation on store call.
 */
public interface RecordValidator
{

    /**
     * Decorate with length validation.
     */
    public static RecordStore.OrderedWritable lengthValidate(
            final RecordReader recordReader,
            final RecordStore.OrderedWritable delegate)
    {
        return new RecordLengthValidator(recordReader, delegate);
    }

    /**
     * Decorate with order validation.
     */
    public static RecordStore.OrderedWritable orderValidate(
            final RecordReader.LongField orderingField,
            final RecordStore.OrderedWritable delegate)
    {
        return new RecordOrderValidator(orderingField, delegate);
    }


    /**
     * Adds record length validation on store call.
     */
    public class RecordLengthValidator implements RecordStore.OrderedWritable
    {
        private final RecordReader recordReader;
        private final RecordStore.OrderedWritable delegate;

        public RecordLengthValidator(final RecordReader recordReader,
                               final RecordStore.OrderedWritable delegate)
        {
            this.recordReader = recordReader;
            this.delegate = delegate;
        }

        @Override
        public void store(final ByteBuffer buffer) throws IOException
        {
            int claim = recordReader.getLength(buffer, 0);
            int actual = buffer.remaining();

            if(claim != actual)
            {
                throw new IOException("Invalid record length, says: " + claim +
                        ", actual " + actual);
            }
            else
            {
                delegate.store(buffer);
            }
        }

        @Override
        public int available()
        {
            return delegate.available();
        }

        @Override
        public void closeWrite() throws IOException
        {
            delegate.closeWrite();
        }

        @Override
        public RecordBuffer extractRange(final long from, final long to)
                throws IOException
        {
            return delegate.extractRange(from, to);
        }

        @Override
        public void forEach(final Consumer<RecordBuffer> action,
                            final long from, final long to) throws IOException
        {
            delegate.forEach(action, from, to);
        }

    }


    /**
     * Adds record order validation on store call.
     */
    public class RecordOrderValidator implements RecordStore.OrderedWritable
    {

        private final RecordReader.LongField orderingField;
        private final RecordStore.OrderedWritable delegate;

        private long lastVal = Long.MIN_VALUE;

        public RecordOrderValidator(final RecordReader.LongField orderingField,
                                    final RecordStore.OrderedWritable delegate)
        {
            this.orderingField = orderingField;
            this.delegate = delegate;
        }

        @Override
        public void store(final ByteBuffer buffer) throws IOException
        {
            long value = orderingField.value(buffer, 0);

            if(value < lastVal)
            {
                throw new IOException("Invalid record order, last: " + lastVal +
                        ", current " + value);
            }
            else
            {
                delegate.store(buffer);
               lastVal = value;
            }
        }

        @Override
        public int available()
        {
            return delegate.available();
        }

        @Override
        public void closeWrite() throws IOException
        {
            delegate.closeWrite();
        }

        @Override
        public RecordBuffer extractRange(final long from, final long to)
                throws IOException
        {
            return delegate.extractRange(from, to);
        }

        @Override
        public void forEach(final Consumer<RecordBuffer> action,
                            final long from, final long to) throws IOException
        {
            delegate.forEach(action, from, to);
        }

    }

}

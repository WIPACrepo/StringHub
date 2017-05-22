package icecube.daq.performance.binary.store.impl.test;

import icecube.daq.performance.binary.buffer.RangeSearch;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.buffer.RecordBufferIndex;
import icecube.daq.performance.binary.buffer.RecordBuffers;
import icecube.daq.performance.binary.buffer.WritableRecordBuffer;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.store.RecordStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * Mocks for testing RecordStores.
 */
public class Mock
{

    public static class MethodTrack
    {
        List<String> methodCalls = new LinkedList<>();

        public void assertCalls(String... expectedCalls)
        {
            for (int i = 0; i < expectedCalls.length; i++)
            {
                if(methodCalls.size() > i)
                {
                    assertEquals(expectedCalls[i], methodCalls.get(i));
                }
                else
                {
                    fail("missing call to " + expectedCalls[i]);
                }
            }

            if(methodCalls.size() > expectedCalls.length)
            {
                int extra = methodCalls.size() - expectedCalls.length;
                for(int i=expectedCalls.length; i<methodCalls.size(); i++)
                {
                    System.out.println("NOT EXPECTED: " + methodCalls.get(i));
                }
                fail(extra + " unexpected method calls.");
            }
        }

        public void assertCertainCalls(String prefix, String... expectedCalls)
        {
            List<String> candidates = new LinkedList<>();
            for( String call : methodCalls)
            {
                if(call.startsWith(prefix))
                {
                    candidates.add(call);
                }
            }

            for (int i = 0; i < expectedCalls.length; i++)
            {
                if(candidates.size() > i)
                {
                    assertEquals(expectedCalls[i], candidates.get(i));
                }
                else
                {
                    fail("missing call to " + expectedCalls[i]);
                }
            }

            if(candidates.size() > expectedCalls.length)
            {
                int extra = candidates.size() - expectedCalls.length;
                for(int i=expectedCalls.length; i<candidates.size(); i++)
                {
                    System.out.println("NOT EXPECTED: " + candidates.get(i));
                }
                fail(extra + " unexpected method calls.");
            }
        }

        public void reset()
        {
            methodCalls = new LinkedList<>();
        }

    }

    public static class MockStore extends MethodTrack
            implements RecordStore.Prunable
    {

        final boolean advanceBufferOnStore;

        public MockStore()
        {
            this(false);
        }

        public MockStore(final boolean advanceBufferOnStore)
        {
            this.advanceBufferOnStore = advanceBufferOnStore;
        }

        @Override
        public void prune(final long boundaryValue)
        {
            methodCalls.add("prune(" + boundaryValue + ")");
        }

        @Override
        public RecordBuffer extractRange(final long from, final long to) throws IOException
        {
            methodCalls.add("extractRange(" + from +", " + to + ")");
            return RecordBuffers.EMPTY_BUFFER;
        }

        @Override
        public void forEach(final Consumer<RecordBuffer> action, final long from, final long to) throws IOException
        {
            methodCalls.add("forEach(" + action + ", " + from + ", " + to + ")");
        }

        @Override
        public void store(final ByteBuffer buffer) throws IOException
        {
            methodCalls.add("store(" + buffer.toString() + ")");

            // simulate consuming the buffer
            if(advanceBufferOnStore)
            {
                buffer.position(buffer.limit());
            }
        }

        @Override
        public int available()
        {
            methodCalls.add("available()");
            return 0;
        }

        @Override
        public void closeWrite() throws IOException
        {
            methodCalls.add("closeWrite()");
        }
    }


    public static class MockIndex extends MethodTrack
            implements RecordBufferIndex.UpdatableIndex

    {

        @Override
        public void addIndex(final int position, final long value)
        {
            methodCalls.add("addIndex(" + position + ", " + value + ")");
        }

        @Override
        public void update(final int offset)
        {
            methodCalls.add("update(" + offset + ")");
        }

        @Override
        public void clear()
        {
            methodCalls.add("clear()");
        }

        @Override
        public int lessThan(final long value)
        {
            methodCalls.add("lessThan(" + value + ")");
            return 0;
        }

    }


    public static class MockSearch extends MethodTrack
            implements RangeSearch
    {

        public MockBuffer.IterateMock lastIteratorMock;


        @Override
        public RecordBuffer extractRange(final RecordBuffer buffer,
                                         final RecordBuffer.MemoryMode resultMode,
                                         final long from, final long to)
        {
            methodCalls.add("noop");
            return null;
        }

        @Override
        public RecordBuffer extractRange(final RecordBuffer buffer,
                                         final RecordBuffer.MemoryMode resultMode,
                                         final RecordBufferIndex index,
                                         final long from, final long to)
        {
            methodCalls.add("extractRange(" + buffer + ", " + resultMode +
                    ", " + index + ", " + from + ", " + to + ")");
            lastIteratorMock =  new MockBuffer.IterateMock();
            return lastIteratorMock;
        }

    }

    public static class MockBuffer extends MethodTrack
            implements WritableRecordBuffer.Prunable
    {

        public int size;

        @Override
        public void put(final ByteBuffer data)
        {
            methodCalls.add("put(" + data.toString() + ")");
            size+=data.remaining();
        }

        @Override
        public int available()
        {
            methodCalls.add("available()");
            return 0;
        }

        @Override
        public int getLength()
        {
            methodCalls.add("getLength()");
            return size;
        }

        @Override
        public byte getByte(final int index)
        {
            methodCalls.add("noop");
            return 0;
        }

        @Override
        public short getShort(final int index)
        {
            methodCalls.add("noop");
            return 0;
        }

        @Override
        public int getInt(final int index)
        {
            methodCalls.add("noop");
            return 0;
        }

        @Override
        public long getLong(final int index)
        {
            methodCalls.add("noop");
            return 0;
        }

        @Override
        public byte[] getBytes(final int start, final int length)
        {
            methodCalls.add("noop");
            return new byte[0];
        }

        @Override
        public void copyBytes(final byte[] dest, final int offset, final int start, final int length)
        {
            methodCalls.add("noop");
        }

        @Override
        public RecordBuffer view(final int start, final int length)
        {
            methodCalls.add("noop");
            return null;
        }

        @Override
        public RecordBuffer copy(final int start, final int length)
        {
            methodCalls.add("noop");
            return null;
        }

        @Override
        public void prune(final int count)
        {
            methodCalls.add("prune(" + count + ")");
        }

        public static class IterateMock extends MockBuffer
        {
            Consumer<? super RecordBuffer> action;

            @Override
            public Iterable<RecordBuffer>
            eachRecord(final RecordReader recordReader)
            {
                return new Iterable<RecordBuffer>()
                {
                    @Override
                    public void forEach(final Consumer<? super RecordBuffer> action)
                    {
                        if(IterateMock.this.action == null)
                        {
                            IterateMock.this.action=action;
                        }
                    }

                    @Override
                    public Iterator<RecordBuffer> iterator()
                    {
                        return new Iterator<RecordBuffer>()
                        {
                            @Override
                            public boolean hasNext()
                            {
                                return false;
                            }

                            @Override
                            public RecordBuffer next()
                            {
                                return null;
                            }
                        };
                    }
                };
            }

            public void assertConsumer(Consumer<? extends RecordBuffer> consumer)
            {
                assertSame(this.action, consumer);
            }

        }

    }

    public static class DelegatingIndex extends MethodTrack
            implements RecordBufferIndex.UpdatableIndex

    {
        private final RecordBufferIndex.UpdatableIndex delegate;

        public DelegatingIndex(final UpdatableIndex delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void addIndex(final int position, final long value)
        {
            methodCalls.add("addIndex(" + position + ", " + value + ")");
            delegate.addIndex(position, value);
        }

        @Override
        public void update(final int offset)
        {
            methodCalls.add("update(" + offset + ")");
            delegate.update(offset);
        }

        @Override
        public void clear()
        {
            methodCalls.add("clear()");
            delegate.clear();
        }

        @Override
        public int lessThan(final long value)
        {
            methodCalls.add("lessThan(" + value + ")");
            return delegate.lessThan(value);
        }

    }


    public static class DelegatingBuffer extends MethodTrack
            implements WritableRecordBuffer.Prunable
    {

        private final WritableRecordBuffer.Prunable delegate;

        public DelegatingBuffer(final Prunable delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void put(final ByteBuffer data)
        {
            methodCalls.add("put(" + data.toString() + ")");
            delegate.put(data);
        }

        @Override
        public int available()
        {
            methodCalls.add("available()");
            return delegate.available();
        }

        @Override
        public int getLength()
        {
            methodCalls.add("getLength()");
            return  delegate.getLength();
        }

        @Override
        public byte getByte(final int index)
        {
            methodCalls.add("getByte(" + index + ")");
            return delegate.getByte(index);
        }

        @Override
        public short getShort(final int index)
        {
            methodCalls.add("getShort(" + index + ")");
            return delegate.getShort(index);
        }

        @Override
        public int getInt(final int index)
        {
            methodCalls.add("getInt(" + index + ")");
            return delegate.getInt(index);
        }

        @Override
        public long getLong(final int index)
        {
            methodCalls.add("getLong(" + index + ")");
            return delegate.getLong(index);
        }

        @Override
        public byte[] getBytes(final int start, final int length)
        {
            methodCalls.add("getBytes(" + start + ", " + length + ")");
            return delegate.getBytes(start, length);
        }

        @Override
        public void copyBytes(final byte[] dest, final int offset, final int start, final int length)
        {
            methodCalls.add("noop");
            delegate.copyBytes(dest, offset, start, length);
        }

        @Override
        public RecordBuffer view(final int start, final int length)
        {
            methodCalls.add("noop");
            return delegate.view(start, length);
        }

        @Override
        public RecordBuffer copy(final int start, final int length)
        {
            methodCalls.add("noop");
            return delegate.copy(start, length);
        }

        @Override
        public void prune(final int count)
        {
            methodCalls.add("prune(" + count + ")");
            delegate.prune(count);
        }

    }


}

package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.IndexFactory;
import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.record.RecordReader;
import icecube.daq.performance.binary.test.Assertions;
import icecube.daq.performance.binary.test.OrderedDataCase;
import icecube.daq.performance.binary.test.RandomOrderedValueSequence;
import icecube.daq.performance.binary.test.RecordGenerator;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests ExpandingMemoryRecordStore;
 */
public class ExpandingMemoryRecordStoreTest
{

    RecordGenerator[] commonRecordTypes = new RecordGenerator[]
            {
                    new RecordGenerator.DAQRecordProvider(1024),
                    new RecordGenerator.RandomLengthRecordProvider(1024),
                    new RecordGenerator.SmallRecordProvider()
            };


    @Test
    public void testAvailable() throws IOException
    {
        ///
        /// test that ExpandingMemoryRecordStore.available() always
        /// returns Integer.MAX_VALUE
        ///

        RecordGenerator.DAQRecordProvider generator =
                new RecordGenerator.DAQRecordProvider(123);
        ExpandingMemoryRecordStore subject =
                new ExpandingMemoryRecordStore(generator.recordReader(),
                        generator.orderingField(), 1024, IndexFactory.NO_INDEX);

        assertEquals(Integer.MAX_VALUE, subject.available());

        subject.store(generator.generate(1111));
        subject.store(generator.generate(2222));
        subject.store(generator.generate(3333));

        assertEquals(Integer.MAX_VALUE, subject.available());


    }

    @Test
    public void testPrune() throws IOException
    {
        RecordGenerator generator =
                new RecordGenerator.FixedLengthRecordProvider(100);

        ExpandingMemoryRecordStore subject =
                new ExpandingMemoryRecordStore(generator.recordReader(),
                        generator.orderingField(), 1000, IndexFactory.NO_INDEX);

        RandomOrderedValueSequence seq = new RandomOrderedValueSequence();

        // fill 10 segments
        long[][] segmentBounds = new long[10][];
        for (int i = 0; i < segmentBounds.length; i++)
        {
            long first = seq.next(false);
            subject.store(generator.generate(first));
            subject.store(generator.generate(seq.next()));
            subject.store(generator.generate(seq.next()));
            subject.store(generator.generate(seq.next()));
            subject.store(generator.generate(seq.next()));
            subject.store(generator.generate(seq.next()));
            subject.store(generator.generate(seq.next()));
            subject.store(generator.generate(seq.next()));
            subject.store(generator.generate(seq.next()));
            long last = seq.next();
            subject.store(generator.generate(last));

            segmentBounds[i] = new long[]{first, last};
        }

        for (int i = 0; i < segmentBounds.length; i++)
        {
            long start = segmentBounds[i][0];
            long end = segmentBounds[i][1];

            // pre-prune
            RecordBuffer answer = subject.extractRange(start, end);
            assertEquals(1000, answer.getLength());
            Assertions.assertBounds("", generator.recordReader(),
                    generator.orderingField(), answer, start, end);

            subject.prune(end+1);

            // post-prune
            answer = subject.extractRange(start, end);
            assertEquals(0, answer.getLength());
        }

    }

    @Test
    public void testQuery() throws IOException
    {
        for (int i = 0; i < commonRecordTypes.length; i++)
        {
            testQuery(commonRecordTypes[i]);
        }
    }

    public void testQuery(RecordGenerator generator) throws IOException
    {
        ExpandingMemoryRecordStore subject =
                new ExpandingMemoryRecordStore(generator.recordReader(),
                        generator.orderingField(), 1024, IndexFactory.NO_INDEX);

        OrderedDataCase dataCase = OrderedDataCase.STANDARD_CASE;

        generator.fill(dataCase.ordinals(), subject);


        for(OrderedDataCase.QueryCase query : dataCase.queries())
        {
            RecordBuffer answer =
                    subject.extractRange(query.from, query.to);
            Assertions.assertContainsExactly("",
                    generator.recordReader(),
                    generator.orderingField(),
                    answer, query.expected);
        }
    }

    @Test
    public void testForEach() throws IOException
    {
        for (int i = 0; i < commonRecordTypes.length; i++)
        {
            testForEach(commonRecordTypes[i]);
        }
    }

    public void testForEach(RecordGenerator generator) throws IOException
    {
        ExpandingMemoryRecordStore subject =
                new ExpandingMemoryRecordStore(generator.recordReader(),
                        generator.orderingField(), 1024, IndexFactory.NO_INDEX);

        OrderedDataCase dataCase = OrderedDataCase.STANDARD_CASE;

        generator.fill(dataCase.ordinals(), subject);


        for(OrderedDataCase.QueryCase query : dataCase.queries())
        {
            final List<Long> values = new ArrayList<>();
            Consumer<RecordBuffer> collector = new Consumer<RecordBuffer>()
            {
                @Override
                public void accept(final RecordBuffer buffer)
                {
                    values.add(generator.orderingField().value(buffer, 0));
                }
            };

            subject.forEach(collector, query.from, query.to);

            Assertions.assertContainsExactly("", values, query.expected);
        }
    }

    @Test
    public void testOverlapingQuery() throws IOException
    {
        for (int i = 0; i < commonRecordTypes.length; i++)
        {
            testOverlapingQuery(commonRecordTypes[i]);
        }
    }

    public void testOverlapingQuery(RecordGenerator generator) throws IOException
    {
        ///
        /// test a query that spans multiple segments
        ///
        int SEGMENT_SIZE=1024;
        ExpandingMemoryRecordStore subject =
                new ExpandingMemoryRecordStore(generator.recordReader(),
                        generator.orderingField(), SEGMENT_SIZE, IndexFactory.NO_INDEX);


        //1. fill several segments, tracking the values pairs that span
        //   an extent
        class Boundary
        {
            final long lastValSegmentA;
            final long firstValSegmentB;

            Boundary(final long lastOfSegment, final long firstOfSegment)
            {
                this.lastValSegmentA = lastOfSegment;
                this.firstValSegmentB = firstOfSegment;
            }

        }
        List<Boundary> boundaries = new LinkedList<>();
        RandomOrderedValueSequence seq = new RandomOrderedValueSequence();

        int bytesWritten = 0;
        long lastValue = Long.MIN_VALUE;
        while(boundaries.size() < 10)
        {
            long val = seq.next();
            ByteBuffer record = generator.generate(val);
            int size = record.remaining();
            if( (bytesWritten + size) <= SEGMENT_SIZE)
            {
                subject.store(record);
                bytesWritten += size;
                lastValue = val;
            }
            else
            {
                // will spill over into next segment
                subject.store(record);
                bytesWritten = size;
                boundaries.add(new Boundary(lastValue, val));
                lastValue=val;
            }
        }

        // 2. query across each boundary
        for (Boundary bound : boundaries)
        {
            RecordBuffer answer =
                    subject.extractRange(bound.lastValSegmentA,
                            bound.firstValSegmentB);

            // note: the boundary values may repeat
            Assertions.assertContainsAtLeast("", generator.recordReader(),
                    generator.orderingField(), answer,
                    new long[]{bound.lastValSegmentA, bound.firstValSegmentB});
        }

    }


    @Test
    public void testOverlapingForEach() throws IOException
    {
        for (int i = 0; i < commonRecordTypes.length; i++)
        {
            testOverlapingForEach(commonRecordTypes[i]);
        }
    }

    public void testOverlapingForEach(RecordGenerator generator) throws IOException
    {
        ///
        /// test a query that spans multiple segments
        ///
        int SEGMENT_SIZE=1024;
        ExpandingMemoryRecordStore subject =
                new ExpandingMemoryRecordStore(generator.recordReader(),
                        generator.orderingField(), SEGMENT_SIZE, IndexFactory.NO_INDEX);


        //1. fill several segments, tracking the values pairs that span
        //   an extent
        class Boundary
        {
            final long lastValSegmentA;
            final long firstValSegmentB;

            Boundary(final long lastOfSegment, final long firstOfSegment)
            {
                this.lastValSegmentA = lastOfSegment;
                this.firstValSegmentB = firstOfSegment;
            }

        }
        List<Boundary> boundaries = new LinkedList<>();
        RandomOrderedValueSequence seq = new RandomOrderedValueSequence();

        int bytesWritten = 0;
        long lastValue = Long.MIN_VALUE;
        while(boundaries.size() < 10)
        {
            long val = seq.next();
            ByteBuffer record = generator.generate(val);
            int size = record.remaining();
            if( (bytesWritten + size) <= SEGMENT_SIZE)
            {
                subject.store(record);
                bytesWritten += size;
                lastValue = val;
            }
            else
            {
                // will spill over into next segment
                subject.store(record);
                bytesWritten = size;
                boundaries.add(new Boundary(lastValue, val));
                lastValue=val;
            }
        }

        // 2. query across each boundary
        for (Boundary bound : boundaries)
        {
            final List<Long> values = new ArrayList<>();
            Consumer<RecordBuffer> collector = new Consumer<RecordBuffer>()
            {
                @Override
                public void accept(final RecordBuffer buffer)
                {
                    values.add(generator.orderingField().value(buffer, 0));
                }
            };

            subject.forEach(collector, bound.lastValSegmentA,
                    bound.firstValSegmentB);

            Assertions.assertContainsAtLeast("",
                    values,
                    new long[]{bound.lastValSegmentA, bound.firstValSegmentB});
        }
    }


    @Test
    public void testOverlapingQueryWithRepeatingValues() throws IOException
    {
        ///
        /// test a query that spans multiple segments with the same value
        /// repeating across the boundary.
        int RECORD_SIZE=128;
        RecordGenerator.FixedLengthRecordProvider generator =
                new RecordGenerator.FixedLengthRecordProvider(128);

        int SEGMENT_SIZE = 133 * RECORD_SIZE;
        ExpandingMemoryRecordStore subject =
                new ExpandingMemoryRecordStore(generator.recordReader(),
                        generator.orderingField(), SEGMENT_SIZE,
                        IndexFactory.NO_INDEX);

        RandomOrderedValueSequence seq = new RandomOrderedValueSequence(9999);

        // 8 repeating values spanning segment 1-2
        //
        long val=0;
        for(int i = 0; i < (133-5); i++)
        {
            val = seq.next();
            subject.store(generator.generate(val));
        }
        long repeat = seq.next(false);// ensure not a repeat of last
        subject.store(generator.generate(repeat));
        subject.store(generator.generate(repeat));
        subject.store(generator.generate(repeat));
        subject.store(generator.generate(repeat));
        subject.store(generator.generate(repeat));

        subject.store(generator.generate(repeat));
        subject.store(generator.generate(repeat));
        subject.store(generator.generate(repeat));

        repeat = seq.next(false);// ensure not a repeat of last
        subject.store(generator.generate(seq.next()));
        subject.store(generator.generate(seq.next()));
        subject.store(generator.generate(seq.next()));
        subject.store(generator.generate(seq.next()));
        subject.store(generator.generate(seq.next()));

        RecordBuffer answer = subject.extractRange(repeat, repeat);

        long [] expected = new long[]{repeat, repeat, repeat, repeat,
                                      repeat, repeat, repeat, repeat};
        Assertions.assertContainsExactly("", generator.recordReader(),
                generator.orderingField(), answer, expected);

    }



}

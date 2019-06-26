package icecube.daq.performance.diagnostic;

import sun.misc.Contended;

import java.util.concurrent.atomic.LongAdder;

/**
 * Defines interfaces for measuring throughput of components that
 * handle messages.
 *
 * The inheritance model for meters is tortured by competing desires
 * of a unified interface, the variation of metered quantities and
 * minimum reporting overhead.
 *
 * The implementation skews performance to the benefit of the reporter
 * which will be the dominant method invoker.
 *
 * Metering is not synchronized. Atomics and volatiles are employed to
 * maintain consistency of individual counters, but Samples are not
 * necessarily consistent between counters.  Implementations attempt
 * to bias Samples to over-report input rather than output to avoid
 * calculating negative values for buffered data.
 */
public interface Metered
{

    /**
     * The quantities being metered.
     */
    public class Sample
    {
        public final long msgIn;
        public final long bytesIn;
        public final long msgOut;
        public final long bytesOut;
        public final long utcIn;
        public final long utcOut;

        public Sample(final long msgIn, final long bytesIn,
                      final long msgOut, final long bytesOut)
        {
            this.msgIn = msgIn;
            this.bytesIn = bytesIn;
            this.msgOut = msgOut;
            this.bytesOut = bytesOut;
            this.utcIn = 0;
            this.utcOut = 0;
        }

        public Sample(final long msgIn, final long bytesIn,
                         final long msgOut, final long bytesOut,
                         final long utcIn, final long utcOut)
        {
            this.msgIn = msgIn;
            this.bytesIn = bytesIn;
            this.msgOut = msgOut;
            this.bytesOut = bytesOut;
            this.utcIn = utcIn;
            this.utcOut = utcOut;
        }
    }

    /**
     * Read the current counter quantities. Counters are read
     * en masse (rather than one-by-one) for consistency of
     * calculated values.
     */
    public Sample getSample();



    /**
     * Defines the reporting side of a synchronous handler.
     */
    public interface Throughput extends Metered
    {
        public void report(final int size);
        public void report(final int msgCount, final int size);

    }

    /**
     * Defines the reporting side of an asynchronous handler.
     */
    public interface Buffered extends Metered
    {
        public void reportIn(final int size);
        public void reportIn(final int msgCount, final int size);
        public void reportOut(final int size);
        public void reportOut(final int msgCount, final int size);
    }

    /**
     * Defines the reporting side of a synchronous handler
     * of ordered UTC data.
     */
    public interface UTCThroughput extends Metered
    {
        public void report(final int size, final long utc);
        public void report(final int msgCount, final int size, final long utc);
    }

    /**
     * Defines the reporting side of a asynchronous handler
     * of ordered UTC data.
     */
    public interface UTCBuffered extends Metered
    {
        public void reportIn(final int size, final long utc);
        public void reportIn(final int msgCount, final int size,
                             final long utc);
        public void reportOut(final int size, final long utc);
        public void reportOut(final int msgCount, final int size,
                              final long utc);
    }


    /**
     * A null implementation.
     */
    public class NullMeter implements Throughput, Buffered,
            UTCThroughput, UTCBuffered
    {
        Sample nullSample = new Sample(0, 0, 0, 0, 0, 0);

        @Override
        public Sample getSample()
        {
            return nullSample;
        }

        @Override
        public final void reportIn(final int size)
        {
        }

        @Override
        public final void reportIn(final int msgCount, final int size)
        {
        }

        @Override
        public final void reportOut(final int size)
        {
        }

        @Override
        public final void reportOut(final int msgCount, final int size)
        {
        }

        @Override
        public final void report(final int size)
        {
        }

        @Override
        public final void report(final int msgCount, final int size)
        {
        }

        @Override
        public final void reportIn(final int size, final long utc)
        {
        }

        @Override
        public final void reportIn(final int msgCount, final int size,
                                   final long utc)
        {
        }

        @Override
        public final void reportOut(final int size, final long utc)
        {
        }

        @Override
        public final void reportOut(final int msgCount, final int size,
                                    final long utc)
        {
        }

        @Override
        public final void report(final int size, final long utc)
        {
        }

        @Override
        public final void report(final int msgCount, final int size,
                                 final long utc)
        {
        }
    }


    /**
     * An implementation with sentinel values to indicate
     * that metering is inoperable.
     */
    public class DisabledMeter extends NullMeter
    {
        Sample disabledSample = new Sample(-1, -1, -1, -1, -1, -1);

        @Override
        public Sample getSample()
        {
            return disabledSample;
        }
    }


    /**
     * Implementations are held in a container to reduce visibility and
     * promoted access via the factory methods. (All top level classes
     * in an interface are public)
     */
    public class Factory
    {

        /**
         * To select the most efficient metering implementation,
         * clients may specify their concurrency model.
         */
        public static enum ConcurrencyModel
        {
            MPMC,  // multiple threads reporting input and
                   // multiple threads reporting output

            SPSC   // single thread reporting input and
                   // single thread reporting output
        }

        // Factory methods are provided to narrow the scope of
        // the reporting interface to the needs of the client.

        public static Throughput throughputMeter()
        {
            return throughputMeter(ConcurrencyModel.SPSC);
        }

        public static UTCThroughput utcThroughputMeter()
        {
            return utcThroughputMeter(ConcurrencyModel.SPSC);

        }

        public static Buffered bufferMeter()
        {
            return bufferMeter(ConcurrencyModel.SPSC);

        }

        public static UTCBuffered utcBufferMeter()
        {
            return utcBufferMeter(ConcurrencyModel.SPSC);

        }

        public static Throughput throughputMeter(ConcurrencyModel concurrency)
        {
            switch (concurrency)
            {
                case SPSC:
                    return new ThroughputMeterImpl();
                case MPMC:
                    return new ConcurrentThroughputMeterImpl();
                default:
                    throw new IllegalArgumentException("unknown" + concurrency);
            }

        }

        public static UTCThroughput utcThroughputMeter(
                ConcurrencyModel concurrency)
        {
            switch (concurrency)
            {
                case SPSC:
                    return new ThroughputMeterImpl();
                case MPMC:
                    return new ConcurrentThroughputMeterImpl();
                default:
                    throw new IllegalArgumentException("unknown" + concurrency);
            }
        }

        public static Buffered bufferMeter(ConcurrencyModel concurrency)
        {
            switch (concurrency)
            {
                case SPSC:
                    return new BufferedMeterImpl();
                case MPMC:
                    return new ConcurrentBufferedMeterImpl();
                default:
                    throw new IllegalArgumentException("unknown" + concurrency);
            }
        }

        public static UTCBuffered utcBufferMeter(ConcurrencyModel concurrency)
        {
            switch (concurrency)
            {
                case SPSC:
                    return new BufferedMeterImpl();
                case MPMC:
                    return new ConcurrentBufferedMeterImpl();
                default:
                    throw new IllegalArgumentException("unknown" + concurrency);
            }
        }



    /**
     * A throughput meter.
     *
     * Tracks in/out quantities in a single member for a small efficiency
     * gain for meters that only track throughput.
     *
     *
     */
    protected static class ThroughputMeterImpl
            implements Throughput, UTCThroughput
    {
        private volatile long msgs;
        private volatile long bytes;
        private volatile long utc;

        @Override
        public void report(final int size)
        {
            report(1, size);
        }

        @Override
        public void report(final int msgCount, final int size)
        {
            this.msgs+=msgCount;
            this.bytes+=size;
        }

        @Override
        public void report(final int size, final long utc)
        {
            report(1, size, utc);
        }

        @Override
        public void report(final int msgCount, final int size, final long utc)
        {
            this.msgs+=msgCount;
            this.bytes+=size;
            this.utc = utc;
        }

        @Override
        public Sample getSample()
        {
            return new Sample(msgs, bytes, msgs, bytes, utc, utc);
        }
    }

    /**
     * A buffered metered
     *
     * Tracks in/out quantities separately to provide support for calculating
     * the amount of data held in the component.
     */
    protected static class BufferedMeterImpl implements  Buffered, UTCBuffered
    {
        @Contended("in")
        private volatile long msgIn;
        @Contended("in")
        private volatile long byteIn;
        @Contended("out")
        private volatile long msgOut;
        @Contended("out")
        private volatile long byteOut;
        @Contended("in")
        private volatile long utcIn;
        @Contended("out")
        private volatile long utcOut;

        @Override
        public void reportIn(final int size)
        {
            reportIn(1, size);
        }

        @Override
        public void reportIn(final int msgCount, final int size)
        {
            msgIn+=msgCount;
            byteIn+=size;
        }

        @Override
        public void reportOut(final int size)
        {
            reportOut(1, size);
        }

        @Override
        public void reportOut(final int msgCount, final int size)
        {
            msgOut+=msgCount;
            byteOut+=size;
        }

        @Override
        public void reportIn(final int size, final long utc)
        {
            reportIn(1, size, utc);
        }

        @Override
        public void reportIn(final int msgCount, final int size,
                             final long utc)
        {
            this.msgIn+=msgCount;
            this.byteIn+=size;
            utcIn = utc;
        }

        @Override
        public void reportOut(final int size, final long utc)
        {
            reportOut(1, size, utc);
        }

        @Override
        public void reportOut(final int msgCount, final int size,
                              final long utc)
        {
            this.msgOut+=msgCount;
            this.byteOut+=size;
            utcOut = utc;
        }

        @Override
        public Sample getSample()
        {
            // access output first to prefer an over-count of held
            // data over an under-count
            long localMsgOut = msgOut;
            long localByteOut = byteOut;
            long localUtcOut = utcOut;
            return new Sample(msgIn, byteIn, localMsgOut, localByteOut,
                    utcIn, localUtcOut);
        }

    }

        /**
         * A throughput metered that supports multiple threads reporting.
         */
        protected static class ConcurrentThroughputMeterImpl
                implements Throughput, UTCThroughput
        {
            private LongAdder msgs = new LongAdder();
            private LongAdder bytes = new LongAdder();
            private volatile long utc;

            @Override
            public void report(final int size)
            {
                report(1, size);
            }

            @Override
            public void report(final int msgCount, final int size)
            {
                this.msgs.add(msgCount);
                this.bytes.add(size);
            }

            @Override
            public void report(final int size, final long utc)
            {
                report(1, size, utc);
            }

            @Override
            public void report(final int msgCount, final int size,
                               final long utc)
            {
                this.msgs.add(msgCount);
                this.bytes.add(size);
                this.utc = utc;
            }

            @Override
            public Sample getSample()
            {
                long msgIn = msgs.longValue();
                long bytesIn = bytes.longValue();
                return new Sample(msgIn, bytesIn, msgIn, bytesIn, utc, utc);
            }
        }

        /**
         * A buffered metered that supports multiple threads reporting
         * input and multiple threads reporting output.
         */
        protected static class ConcurrentBufferedMeterImpl
                implements  Buffered, UTCBuffered
        {
            private LongAdder msgIn = new LongAdder();
            private LongAdder byteIn = new LongAdder();
            private LongAdder msgOut = new LongAdder();
            private LongAdder byteOut = new LongAdder();
            @Contended("in")
            private volatile long utcIn;
            @Contended("out")
            private volatile long utcOut;

            @Override
            public void reportIn(final int size)
            {
                reportIn(1, size);
            }

            @Override
            public void reportIn(final int msgCount, final int size)
            {
                msgIn.add(msgCount);
                byteIn.add(size);
            }

            @Override
            public void reportOut(final int size)
            {
                reportOut(1, size);
            }

            @Override
            public void reportOut(final int msgCount, final int size)
            {
                msgOut.add(msgCount);
                byteOut.add(size);
            }

            @Override
            public void reportIn(final int size, final long utc)
            {
                reportIn(1, size, utc);
            }

            @Override
            public void reportIn(final int msgCount, final int size,
                                 final long utc)
            {

                msgIn.add(msgCount);
                byteIn.add(size);
                utcIn = utc;
            }

            @Override
            public void reportOut(final int size, final long utc)
            {
                reportOut(1, size, utc);
            }

            @Override
            public void reportOut(final int msgCount, final int size,
                                  final long utc)
            {
                msgOut.add(msgCount);
                byteOut.add(size);
                utcOut = utc;
            }

            @Override
            public Sample getSample()
            {

                // access output first to prefer an over-count of held
                // data over an under-count
                long localMsgOut = msgOut.longValue();
                long localBytesOut = byteOut.longValue();
                long localUtcOut = utcOut;
                return new Sample(msgIn.longValue(), byteIn.longValue(),
                        localMsgOut, localBytesOut,
                        utcIn, localUtcOut);
            }

        }


    }


}

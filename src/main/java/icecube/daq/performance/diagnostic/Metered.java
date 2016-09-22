package icecube.daq.performance.diagnostic;

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
     * Tracks throughput in message and byte count
     */
    public class ThroughputMeter implements Throughput
    {
        private long msgs;
        private long bytes;

        public void report(final int size)
        {
            report(1, size);
        }

        public void report(final int msgCount, final int size)
        {
            this.msgs+=msgCount;
            this.bytes+=size;
        }

        @Override
        public Sample getSample()
        {
            return new Sample(msgs, bytes, msgs, bytes);
        }
    }


    /**
     * Adds UTC timestamp tracking to the throughput meter.
     */
    public class UTCThroughputMeter implements UTCThroughput
    {
        private long msgs;
        private long bytes;
        private long utc;


        public void report(final int size, final long utc)
        {
            report(1, size, utc);
        }

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
     * Breaks input/output reporting to track held (buffered) data.
     */
    public class BufferMeter implements Buffered
    {
        private long msgIn;
        private long byteIn;
        private long msgOut;
        private long byteOut;

        public void reportIn(final int size)
        {
            reportIn(1, size);
        }

        public void reportIn(final int msgCount, final int size)
        {
            this.msgIn+=msgCount;
            this.byteIn+=size;
        }

        public void reportOut(final int size)
        {
            reportOut(1, size);
        }

        public void reportOut(final int msgCount, final int size)
        {
            this.msgOut+=msgCount;
            this.byteOut+=size;
        }

        @Override
        public Sample getSample()
        {
            return new Sample(msgIn, byteIn, msgOut, byteOut);
        }
    }


    /**
     * Adds tracking for utc timestamps to a buffered meter.
     */
    public class UTCBufferMeter implements UTCBuffered
    {

        private long msgIn;
        private long byteIn;
        private long msgOut;
        private long byteOut;
        private long utcIn;
        private long utcOut;


        public void reportIn(final int size, final long utc)
        {
            reportIn(1, size, utc);
        }

        public void reportIn(final int msgCount, final int size,
                             final long utc)
        {
            this.msgIn+=msgCount;
            this.byteIn+=size;
            utcIn = utc;
        }

        public void reportOut(final int size, final long utc)
        {
            reportOut(1, size, utc);
        }

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
            return new Sample(msgIn, byteIn, msgOut, byteOut,
                    utcIn, utcOut);
        }
    }


}

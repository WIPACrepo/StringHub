package icecube.daq.performance.diagnostic;

import java.util.ArrayList;
import java.util.List;

/**
 * MeterContent provides trace content derived from an
 * Metered instance.
 *
 * The fields of the trace content is configurable via an
 * enumeration of the fields that are derivable from meter
 * instances.
 */
public class MeterContent implements Content
{

    /**
     * Groups fields together into some common display styles for simpler,
     * abbreviated construction..
     *
     * Note that runtime type checking is in play for styles that
     * utilize UTC fields.
     */
    public static enum Style
    {
        MSG_RATE_IN
                {
                    @Override
                    MeterField[] fields()
                    {
                        return new MeterField[]
                                {
                                        MeterField.MSGIN,
                                        MeterField.MSPS_IN}
                                ;
                    }
                },
        MSG_RATE_OUT
                {
                    @Override
                    MeterField[] fields()
                    {
                        return new MeterField[]
                                {
                                        MeterField.MSGOUT,
                                        MeterField.MSPS_OUT}
                                ;
                    }
                },
        DATA_RATE_IN
                {
                    @Override
                    MeterField[] fields()
                    {
                        return new MeterField[]
                                {
                                        MeterField.MBIN,
                                        MeterField.MBPS_IN
                                };
                    }
                },
        DATA_RATE_OUT
                {
                    @Override
                    MeterField[] fields()
                    {
                        return new MeterField[]
                                {
                                        MeterField.MBOUT,
                                        MeterField.MBPS_OUT
                                };
                    }
                },
        HELD_DATA
                {
                    @Override
                    MeterField[] fields()
                    {
                        //return new BufferedMeterContent(meter, name);
                        return new MeterField[]
                                {
                                        MeterField.MSGQ,
                                        MeterField.MBQ
                                };
                    }
                },

        /*NOTE: Requires a UTC meter */
        UTC_DELAY
                {
                    @Override
                    MeterField[] fields()
                    {
                        //return new BufferedMeterContent(meter, name);
                        return new MeterField[]
                                {
                                        MeterField.UTC_DELAY_MILLIS
                                };
                    }
                },

        /*All fields including those requiring a UTC meter. */
        ALL
                {
                    @Override
                    MeterField[] fields()
                    {
                        return MeterField.values();
                    }
                };


        abstract MeterField[] fields();
    }


    final Metered meter;
    final MeterField[] fields;

    final String header;

    Metered.Sample lastSample;
    long lastNano;


    static final int BYTES_PER_MB =      1024*1024;
    static final long UTC_PER_MILLIS =   10000000;
    static final long NANOS_PER_MILLIS = 1000000000;

    public MeterContent(final Metered meter, Style... styles)
    {
        this(meter, extractFields(styles));
    }

    public MeterContent(final Metered meter, MeterField... fields)
    {
        this.meter = meter;
        this.fields = fields;
        this.lastSample = meter.getSample();
        this.lastNano = System.nanoTime();

        StringBuilder hb = new StringBuilder();
        for (int i = 0; i < fields.length; i++)
        {
            hb.append(fields[i].header());
        }
        this.header = hb.toString();

    }

    @Override
    public void header(final StringBuilder sb)
    {
        sb.append(header);
    }

    @Override
    public void content(final StringBuilder sb)
    {
        Metered.Sample sample = meter.getSample();
        long now = System.nanoTime();
        float interval = (now - lastNano) / 1000000000f;

        for (int i = 0; i < fields.length; i++)
        {
            fields[i].content(sb, lastSample, sample, interval);
        }

        lastSample = sample;
        lastNano = now;
    }

    static MeterField[] extractFields(Style... styles)
    {
        List<MeterField> acc = new ArrayList<>();
        for (int i = 0; i < styles.length; i++)
        {
            MeterField[] fields = styles[i].fields();
            for (int j = 0; j < fields.length; j++)
            {
                acc.add(fields[j]);
            }
        }
        return acc.toArray(new MeterField[acc.size()]);
    }

    static enum MeterField
    {
        MSGIN(String.format("%-12s", "msgin"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        sb.append(String.format("%-12d", current.msgIn));
                    }
                },
        MSPS_IN(String.format("%-12s", "mpsin"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        long delta = current.msgIn - last.msgIn;
                        long mps =
                                (long) (((float)delta / BYTES_PER_MB) /
                                        secondsInterval);
                        sb.append(String.format("%-12d", mps));
                    }
                },
        MSGOUT(String.format("%-12s", "msgout"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        sb.append(String.format("%-12d", current.msgOut));
                    }
                },
        MSPS_OUT(String.format("%-12s", "mspsout"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        long delta = current.msgOut - last.msgOut;
                        long mps =
                                (long) (((float)delta / BYTES_PER_MB) /
                                        secondsInterval);
                        sb.append(String.format("%-12d", mps));
                    }
                },
        MSGQ(String.format("%-12s", "msgq"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        long delta = current.msgIn - current.msgOut;
                        sb.append(String.format("%-12d", delta));
                    }
                },
        MBIN(String.format("%-12s", "mbin"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        long mbin = current.bytesIn / BYTES_PER_MB;
                        sb.append(String.format("%-12d", mbin));
                    }
                },
        MBPS_IN(String.format("%-12s", "mbpsin"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        long delta = current.bytesOut - last.bytesOut;
                        long mbps =
                                (long) (((float)delta / BYTES_PER_MB) /
                                        secondsInterval);
                        sb.append(String.format("%-12d", mbps));
                    }
                },
        MBOUT(String.format("%-12s", "mbout"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        long mbout = current.bytesIn / BYTES_PER_MB;
                        sb.append(String.format("%-12d", mbout));
                    }
                },
        MBPS_OUT(String.format("%-12s", "mbpsout"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        long delta = current.bytesOut - last.bytesOut;
                        long mbps =
                                (long) (((float)delta / secondsInterval) /
                                        BYTES_PER_MB);
                        sb.append(String.format("%-12d", mbps));
                    }
                },
        MBQ(String.format("%-12s", "mbq"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        long delta = current.bytesIn - current.bytesOut;
                        sb.append(String.format("%-12d", delta/BYTES_PER_MB));
                    }
                },
        UTC_IN(String.format("%-24s", "utcin"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        sb.append(String.format("%-24d", current.utcIn));
                    }
                },
        UTC_OUT(String.format("%-24s", "utcout"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        sb.append(String.format("%-24d", current.utcOut));
                    }
                },
        UTC_DELAY_MILLIS(String.format("%-12s", "delms"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        long delta = current.utcIn - last.utcOut;
                        sb.append(String.format("%-12d", delta/UTC_PER_MILLIS));
                    }
                },
        DATA_MILLIS_PER_SEC_IN(String.format("%-12s", "millipsint"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        long delta = current.utcIn - last.utcIn;
                        long mps =
                            (long) (((float)delta/NANOS_PER_MILLIS) /
                                    secondsInterval);
                        sb.append(String.format("%-12d", mps));
                    }
                },
        DATA_MILLIS_PER_SEC_OUT(String.format("%-12s", "millipsout"))
                {
                    @Override
                    public void content(final StringBuilder sb,
                                        final Metered.Sample last,
                                        final Metered.Sample current,
                                        final float secondsInterval)
                    {
                        long delta = current.utcOut - last.utcOut;
                        long mps =
                            (long) (((float)delta/NANOS_PER_MILLIS) /
                                    secondsInterval);
                        sb.append(String.format("%-12d", mps));
                    }
                };

        final String header;

        MeterField(final String header)
        {
            this.header = header;
        }


        public String header() { return header; }

        public abstract void content(final StringBuilder sb,
                                     final Metered.Sample last,
                                     final Metered.Sample current,
                                     final float secondsInterval);
    }


}
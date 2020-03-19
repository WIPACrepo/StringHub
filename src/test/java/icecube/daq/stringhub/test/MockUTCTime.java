package icecube.daq.stringhub.test;

import icecube.daq.payload.IUTCTime;

public class MockUTCTime
    implements IUTCTime
{
    private long time;

    public MockUTCTime(long time)
    {
        this.time = time;
    }

    @Override
    public int compareTo(Object obj)
    {
        if (obj == null) {
            return 1;
        } else if (!(obj instanceof IUTCTime)) {
            return getClass().getName().compareTo(obj.getClass().getName());
        }

        final long val = ((IUTCTime) obj).longValue();
        if (time < val) {
            return -1;
        } else if (time > val) {
            return 1;
        }

        return 0;
    }

    @Override
    public Object deepCopy()
    {
        return new MockUTCTime(time);
    }

    @Override
    public boolean equals(Object obj)
    {
        return compareTo(obj) == 0;
    }

    @Override
    public IUTCTime getOffsetUTCTime(long ticks)
    {
        return new MockUTCTime(time + ticks);
    }

    @Override
    public int hashCode()
    {
        final long modValue = Integer.MAX_VALUE / 256;

        final long topTwo = time / modValue;

        return (int) (topTwo / modValue) + (int) (topTwo % modValue) +
            (int) (time % modValue);
    }

    @Override
    public long longValue()
    {
        return time;
    }

    @Override
    public long timeDiff(IUTCTime otherTime)
    {
        throw new Error("Unimplemented");
    }

    /**
     * Return a human-readable date/time string
     * @return human-readable date/time string
     */
    @Override
    public String toDateString()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public String toString()
    {
        return Long.toString(time);
    }
}

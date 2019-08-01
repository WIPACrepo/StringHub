package icecube.daq.stringhub.test;

import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.Poolable;

public class MockUTCTime
    implements IUTCTime, Poolable
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

    /**
     * Object is able to dispose of itself.
     * This means it is able to return itself to the pool from
     * which it came.
     */
    @Override
    public void dispose()
    {
        // do nothing
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

    /**
     * Get an object from the pool in a non-static context.
     *
     * @return object of this type from the object pool.
     */
    @Override
    public Poolable getPoolable()
    {
        return new MockUTCTime(-1);
    }

    @Override
    public long longValue()
    {
        return time;
    }

    @Override
    public int hashCode()
    {
        final long modValue = Integer.MAX_VALUE / 256;

        final long topTwo = time / modValue;

        return (int) (topTwo / modValue) + (int) (topTwo % modValue) +
            (int) (time % modValue);
    }

    /**
     * Object knows how to recycle itself
     */
    @Override
    public void recycle()
    {
        // do nothing
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

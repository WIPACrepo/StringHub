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

    public Object deepCopy()
    {
        return new MockUTCTime(time);
    }

    /**
     * Object is able to dispose of itself.
     * This means it is able to return itself to the pool from
     * which it came.
     */
    public void dispose()
    {
        // do nothing
    }

    public boolean equals(Object obj)
    {
        return compareTo(obj) == 0;
    }

    public IUTCTime getOffsetUTCTime(double nanoSec)
    {
        return new MockUTCTime(time + (long) (nanoSec * 10.0));
    }

    /**
     * Get an object from the pool in a non-static context.
     *
     * @return object of this type from the object pool.
     */
    public Poolable getPoolable()
    {
        return new MockUTCTime(-1);
    }

    public long longValue()
    {
        return time;
    }

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
    public void recycle()
    {
        // do nothing
    }

    public long timeDiff(IUTCTime otherTime)
    {
        throw new Error("Unimplemented");
    }

    public double timeDiff_ns(IUTCTime otherTime)
    {
        return (double) (time - otherTime.longValue());
    }

    public String toString()
    {
        return Long.toString(time);
    }
}

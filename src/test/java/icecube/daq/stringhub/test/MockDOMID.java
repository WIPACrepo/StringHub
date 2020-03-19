package icecube.daq.stringhub.test;

import icecube.daq.payload.IDOMID;

public class MockDOMID
    implements IDOMID, Comparable
{
    private long domId;

    public MockDOMID(long domId)
    {
        this.domId = domId;
    }

    @Override
    public int compareTo(Object obj)
    {
        if (obj == null) {
            return 1;
        } else if (!(obj instanceof IDOMID)) {
            return getClass().getName().compareTo(obj.getClass().getName());
        }

        final long val = ((IDOMID) obj).longValue();
        if (domId < val) {
            return -1;
        } else if (domId > val) {
            return 1;
        }

        return 0;
    }

    @Override
    public Object deepCopy()
    {
        return new MockDOMID(domId);
    }

    @Override
    public boolean equals(Object obj)
    {
        return compareTo(obj) == 0;
    }

    @Override
    public int hashCode()
    {
        final long modValue = Integer.MAX_VALUE / 256;

        final long topTwo = domId / modValue;

        return (int) (topTwo / modValue) + (int) (topTwo % modValue) +
            (int) (domId % modValue);
    }

    @Override
    public long longValue()
    {
        return domId;
    }

    @Override
    public String toString()
    {
        return String.format("%012x", domId);
    }
}

package icecube.daq.stringhub.test;

import icecube.daq.payload.ISourceID;
import icecube.daq.payload.Poolable;
import icecube.daq.payload.SourceIdRegistry;

public class MockSourceID
    implements ISourceID, Poolable
{
    private int id;

    public MockSourceID(int id)
    {
        this.id = id;
    }

    @Override
    public int compareTo(Object obj)
    {
        if (obj == null) {
            return 1;
        } else if (!(obj instanceof ISourceID)) {
            return getClass().getName().compareTo(obj.getClass().getName());
        }

        return getSourceID() - ((ISourceID) obj).getSourceID();
    }

    @Override
    public Object deepCopy()
    {
        return new MockSourceID(id);
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

    /**
     * Get an object from the pool in a non-static context.
     *
     * @return object of this type from the object pool.
     */
    @Override
    public Poolable getPoolable()
    {
        return new MockSourceID(-1);
    }

    @Override
    public int getSourceID()
    {
        return id;
    }

    @Override
    public int hashCode()
    {
        return id;
    }

    /**
     * Object knows how to recycle itself
     */
    @Override
    public void recycle()
    {
        // do nothing
    }

    /**
     * Get the string representation of this source ID.
     *
     * @return DAQName#DAQId
     */
    @Override
    public String toString()
    {
        return SourceIdRegistry.getDAQNameFromSourceID(id) + "#" +
            SourceIdRegistry.getDAQIdFromSourceID(id);
    }
}

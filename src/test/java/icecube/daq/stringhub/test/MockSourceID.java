package icecube.daq.stringhub.test;

import icecube.daq.payload.ISourceID;
import icecube.daq.payload.SourceIdRegistry;

public class MockSourceID
    implements ISourceID
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

    @Override
    public boolean equals(Object obj)
    {
        return compareTo(obj) == 0;
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

package icecube.daq.sender.test;

import icecube.daq.util.DOMInfo;
import icecube.daq.util.DOMRegistryException;
import icecube.daq.util.IDOMRegistry;

import java.util.Set;

/**
 *
 */
public class MockRegistry implements IDOMRegistry
{

    short channelID;

    DOMInfo domInfo;

    public void setChannelID(short channelID)
    {
        this.channelID = channelID;
    }

    public void setDomInfo(final DOMInfo domInfo)
    {
        this.domInfo = domInfo;
    }

    @Override
    public Iterable<DOMInfo> allDOMs() throws DOMRegistryException
    {
        return null;
    }

    @Override
    public double distanceBetweenDOMs(final DOMInfo dom0, final DOMInfo dom1)
    {
        return 0;
    }

    @Override
    public double distanceBetweenDOMs(final short chan0, final short chan1)
    {
        return 0;
    }

    @Override
    public short getChannelId(final long mbid)
    {
        return channelID;
    }

    @Override
    public DOMInfo getDom(final long mbId)
    {
        return this.domInfo;
    }

    @Override
    public DOMInfo getDom(final int major, final int minor)
    {
        return this.domInfo;
    }

    @Override
    public DOMInfo getDom(final short channelId)
    {
        return this.domInfo;
    }

    @Override
    public Set<DOMInfo> getDomsOnHub(final int hubId) throws DOMRegistryException
    {
        return null;
    }

    @Override
    public Set<DOMInfo> getDomsOnString(final int string) throws DOMRegistryException
    {
        return null;
    }

    @Override
    public String getName(final long mbid)
    {
        return null;
    }

    @Override
    public String getProductionId(final long mbid)
    {
        return null;
    }

    @Override
    public int getStringMajor(final long mbid)
    {
        return 0;
    }

    @Override
    public int getStringMinor(final long mbid)
    {
        return 0;
    }

    @Override
    public int size() throws DOMRegistryException
    {
        return 0;
    }
}
package icecube.daq.stringhub.test;

import icecube.daq.payload.IByteBufferCache;

import java.nio.ByteBuffer;

public class MockBufferCache
    implements IByteBufferCache
{
private static final boolean DEBUG = false;
    private String name;
    private long maxBytesAlloc;
    private int bufsAlloc;
    private long bytesAlloc;

    public MockBufferCache(String name)
    {
        this(name, Long.MIN_VALUE);
    }

    public MockBufferCache(String name, long maxBytesAlloc)
    {
        this.name = name;
        this.maxBytesAlloc = maxBytesAlloc;
    }

    @Override
    public synchronized ByteBuffer acquireBuffer(int bytes)
    {
        bufsAlloc++;
        bytesAlloc += bytes;
if(DEBUG)System.err.println("ALO*"+bytes+"(#"+bufsAlloc+"*"+bytesAlloc+")");
        return ByteBuffer.allocate(bytes);
    }

    @Override
    public void flush()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getCurrentAquiredBuffers()
    {
        return bufsAlloc;
    }

    @Override
    public long getCurrentAquiredBytes()
    {
        return bytesAlloc;
    }

    @Override
    public boolean getIsCacheBounded()
    {
        return maxBytesAlloc > 0;
    }

    @Override
    public long getMaxAquiredBytes()
    {
        return maxBytesAlloc;
    }

    @Override
    public String getName()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getTotalBuffersAcquired()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getTotalBuffersCreated()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public int getTotalBuffersReturned()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getTotalBytesInCache()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public boolean isBalanced()
    {
        return bufsAlloc == 0;
    }

    @Override
    public void receiveByteBuffer(ByteBuffer buf)
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void returnBuffer(ByteBuffer buf)
    {
        returnBuffer(buf.capacity());
    }

    @Override
    public synchronized void returnBuffer(int bytes)
    {
        bufsAlloc--;
        bytesAlloc -= bytes;
if(DEBUG)System.err.println("RTN*"+bytes+"(#"+bufsAlloc+"*"+bytesAlloc+")");
    }

    @Override
    public String toString()
    {
        return "MockBufferCache(" + name + ")[bufs " + bufsAlloc + " bytes " +
            bytesAlloc + "(max " + maxBytesAlloc + ")]";
    }
}

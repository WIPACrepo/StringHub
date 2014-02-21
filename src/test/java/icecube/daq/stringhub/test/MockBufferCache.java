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

    public synchronized ByteBuffer acquireBuffer(int bytes)
    {
        bufsAlloc++;
        bytesAlloc += bytes;
if(DEBUG)System.err.println("ALO*"+bytes+"(#"+bufsAlloc+"*"+bytesAlloc+")");
        return ByteBuffer.allocate(bytes);
    }

    public void destinationClosed()
    {
        throw new Error("Unimplemented");
    }

    public void flush()
    {
        throw new Error("Unimplemented");
    }

    public int getCurrentAquiredBuffers()
    {
        return bufsAlloc;
    }

    public long getCurrentAquiredBytes()
    {
        return bytesAlloc;
    }

    public boolean getIsCacheBounded()
    {
        return maxBytesAlloc > 0;
    }

    public long getMaxAquiredBytes()
    {
        return maxBytesAlloc;
    }

    public String getName()
    {
        throw new Error("Unimplemented");
    }

    public int getTotalBuffersAcquired()
    {
        throw new Error("Unimplemented");
    }

    public int getTotalBuffersCreated()
    {
        throw new Error("Unimplemented");
    }

    public int getTotalBuffersReturned()
    {
        throw new Error("Unimplemented");
    }

    public long getTotalBytesInCache()
    {
        throw new Error("Unimplemented");
    }

    public boolean isBalanced()
    {
        return bufsAlloc == 0;
    }

    public void receiveByteBuffer(ByteBuffer buf)
    {
        throw new Error("Unimplemented");
    }

    public void returnBuffer(ByteBuffer buf)
    {
        returnBuffer(buf.capacity());
    }

    public synchronized void returnBuffer(int bytes)
    {
        bufsAlloc--;
        bytesAlloc -= bytes;
if(DEBUG)System.err.println("RTN*"+bytes+"(#"+bufsAlloc+"*"+bytesAlloc+")");
    }

    public String toString()
    {
        return "MockBufferCache(" + name + ")[bufs " + bufsAlloc + " bytes " +
            bytesAlloc + "(max " + maxBytesAlloc + ")]";
    }
}

package icecube.daq.stringhub.test;

import icecube.daq.payload.IByteBufferCache;

import java.nio.ByteBuffer;

public class MockBufferCache
    implements IByteBufferCache
{
    private int bufsAlloc;
    private int bytesAlloc;

    public MockBufferCache()
    {
    }

    public synchronized ByteBuffer acquireBuffer(int bytes)
    {
        bufsAlloc++;
        bytesAlloc += bytes;
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
        throw new Error("Unimplemented");
    }

    public long getMaxAquiredBytes()
    {
        throw new Error("Unimplemented");
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
        throw new Error("Unimplemented");
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
    }
}

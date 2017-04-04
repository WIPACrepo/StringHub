package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.buffer.RecordBuffer;
import icecube.daq.performance.binary.store.RecordStore;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 * Tests NullRecordStore.java
 */
public class NullRecordStoreTest
{

    RecordStore.OrderedWritable subject  = new NullRecordStore();

    @Test
    public void voidTestDelegation() throws IOException
    {
        ///
        /// Tests that a NullRecordStore is nominally usable as
        /// a record store.

        assertEquals(Integer.MAX_VALUE, subject.available());

        subject.store(ByteBuffer.allocate(0));

        RecordBuffer empty = subject.extractRange(99, 109);
        assertEquals(0, empty.getLength());

        subject.forEach(null, 88, 999);

        subject.closeWrite();

    }



}

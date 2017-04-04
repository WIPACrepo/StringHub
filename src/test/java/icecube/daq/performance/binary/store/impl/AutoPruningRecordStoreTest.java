package icecube.daq.performance.binary.store.impl;

import icecube.daq.performance.binary.store.RecordStore;
import icecube.daq.performance.binary.store.impl.test.Mock;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests AutoPruningRecordStore.java
 */
public class AutoPruningRecordStoreTest
{

    @Test
    public void voidTestDelegation() throws IOException
    {
        ///
        /// Tests that calls are delegated through to base store
        ///
        Mock.MockStore mock = new Mock.MockStore();
        RecordStore.Writable subject = new AutoPruningRecordStore(mock);

        subject.store(ByteBuffer.allocate(123));
        subject.available();
        subject.closeWrite();

        String[] expected = new String[]
                {
                        "store(java.nio.HeapByteBuffer[pos=0 lim=123 cap=123])",
                        "available()",
                        "closeWrite()"
                };
        mock.assertCalls(expected);
    }

    @Test
    public void TestAutoPrune() throws IOException
    {
        ///
        /// Tests that prune(to) is called when a range query (from, to) is
        /// called.
        ///
        Mock.MockStore mock = new Mock.MockStore();
        RecordStore.OrderedWritable subject = new AutoPruningRecordStore(mock);

        for(int i=0; i<100; i++)
        {
            long from = (long) (Math.random() * Long.MAX_VALUE);
            long to = (long) (Math.random() * Long.MAX_VALUE);
            subject.extractRange(from, to);

            mock.reset();
            subject.extractRange(from, to);
            mock.assertCalls("extractRange(" + from + ", " + to + ")",
                             "prune(" + to + ")");

            mock.reset();
            subject.forEach(null, from, to);
            mock.assertCalls("forEach(null, " + from + ", " + to + ")",
                    "prune(" + to + ")");
        }

    }


}

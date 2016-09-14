package icecube.daq.performance.binary.buffer;

import icecube.daq.performance.common.BufferContent;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static icecube.daq.performance.binary.buffer.test.BitUtility.fill;


/**
 * Tests RecordBuffers.ChainedBuffer
 */
@RunWith(value = Parameterized.class)
public class ChainedBufferTest extends CommonRecordBufferTest
{

    @Parameterized.Parameter(0)
    public int[] spans;
    @Parameterized.Parameter(1)
    public String caseName;


    @Parameterized.Parameters(name = "ChainedBuffer[{1}]")
    public static List<Object[]> sizes()
    {
        List<Object[]> cases = new ArrayList<Object[]>(5);
        cases.add(makeParameterizedCase( new int[]{111} ) );
        cases.add(makeParameterizedCase( new int[]{111, 123} ) );
        cases.add(makeParameterizedCase( new int[]{111, 123, 8} ) );
        cases.add(makeParameterizedCase( new int[]{7, 7, 7} ) );
        cases.add(makeParameterizedCase( new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1} ) );
        cases.add(makeParameterizedCase( new int[]{0} ) );
        cases.add(makeParameterizedCase( new int[]{0,0,0} ) );
        cases.add(makeParameterizedCase( new int[]{0,10,0} ) );
        cases.add(makeParameterizedCase( new int[]{0,10,0,3} ) );
        cases.add(makeParameterizedCase( new int[]{0,1,0,1} ) );
        return cases;
    }

    private static Object[] makeParameterizedCase(int[] spans)
    {
        //
        // utility method to make test names include the specific
        // span parameters
        //

        String testName = "[";
        for (int i = 0; i < spans.length; i++)
        {
            testName += spans[i];
            testName += i < spans.length-1 ? ", ":"";
        }
        return new Object[]{spans, testName};
    }


    @Override
    int subjectSize()
    {
        return sum(spans);
    }

    @Override
    RecordBuffer createSubject(final short pattern, final int alignment)
    {
        ByteBuffer[] buffers = makeSpans(spans);
        int startByte=2-alignment;
        for (int i = 0; i < buffers.length; i++)
        {
            startByte = fill(buffers[i], pattern, startByte);
        }

        return new RecordBuffers.ChainedBuffer(wrap(buffers,
                BufferContent.POSITION_TO_LIMIT));
    }

    @Override
    RecordBuffer createSubject(final int pattern, final int alignment)
    {
        ByteBuffer[] buffers = makeSpans(spans);
        int startByte=4-alignment;
        for (int i = 0; i < buffers.length; i++)
        {
            startByte = fill(buffers[i], pattern, startByte);
        }

        return new RecordBuffers.ChainedBuffer(wrap(buffers,
                BufferContent.POSITION_TO_LIMIT));
    }

    @Override
    RecordBuffer createSubject(final long pattern, final int alignment)
    {
        ByteBuffer[] buffers = makeSpans(spans);
        int startByte=8-alignment;
        for (int i = 0; i < buffers.length; i++)
        {
            startByte = fill(buffers[i], pattern, startByte);

        }

        return new RecordBuffers.ChainedBuffer(wrap(buffers,
                BufferContent.POSITION_TO_LIMIT));
    }

    private static int sum(final int[] values)
    {
        int totalSize = 0;
        for (int i = 0; i < values.length; i++)
        {
            totalSize += values[i];
        }
        return totalSize;
    }

    private static ByteBuffer[] makeSpans(final int[] lengths)
    {
        ByteBuffer[] buffers = new ByteBuffer[lengths.length];
        for (int i = 0; i < buffers.length; i++)
        {
            buffers[i] = ByteBuffer.allocate(lengths[i]);
            buffers[i].order(ByteOrder.BIG_ENDIAN);
        }

        return buffers;
    }


    public static RecordBuffer[] wrap(ByteBuffer buffers[], BufferContent content)
    {
        RecordBuffer[] wrapped = new RecordBuffer[buffers.length];
        for (int i = 0; i < buffers.length; i++)
        {
            wrapped[i] = wrap(buffers[i], content);
        }
        return wrapped;
    }

    public static RecordBuffer wrap(ByteBuffer buffer, BufferContent content)
    {
        return RecordBuffers.wrap(buffer, content);
    }
}

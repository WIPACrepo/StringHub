package icecube.daq.bindery;


/**
 * Tests MultiChannelMergeSort.java
 */
public class MultiChannelMergeSortTest extends AbstractChannelSorterTest
{

    @Override
    public ChannelSorter createTestSubject(final BufferConsumer consumer)
    {
        return new MultiChannelMergeSort(nch, consumer, "test-channel");
    }

    @Override
    public OutOfOrderHitPolicy getOutOfOrderHitPolicy()
    {
        return OutOfOrderHitPolicy.DELIVER;
    }

    @Override
    public UnknownMBIDPolicy getUnknownMBIDPolicy()
    {
        return UnknownMBIDPolicy.LOGGED;
    }

    @Override
    public TimestampTrackingPolicy getTimestampTrackingPolicy()
    {
        return TimestampTrackingPolicy.TRACKED;
    }

}
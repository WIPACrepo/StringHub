package icecube.daq.performance.common;

import java.nio.ByteBuffer;

/**
 * Provides use-case specific mechanisms to normalize, slice and
 * copy the content of a ByteBuffer.
 */
public enum BufferContent
{
    /** Data content [position - limit]. */
    POSITION_TO_LIMIT
            {
                public ByteBuffer normalize(final ByteBuffer original)
                {
                    final ByteBuffer copy = original.slice(); //Note: always BIG_ENDIAN!
                    copy.order(original.order());
                    return copy;
                }

                public ByteBuffer copy(final ByteBuffer original)
                {
                    final ByteBuffer tmp = original.asReadOnlyBuffer(); //Note: always BIG_ENDIAN!
                    final ByteBuffer copy = ByteBuffer.allocate(tmp.remaining());
                    copy.put(tmp);
                    copy.order(original.order());
                    copy.flip();
                    return copy;
                }
            },
    /** Data content [0 - position]. */
    ZERO_TO_POSITION
            {
                public ByteBuffer normalize(final ByteBuffer original)
                {
                    final ByteBuffer tmp = original.duplicate();
                    tmp.position(0);
                    tmp.limit(original.position());
                    final ByteBuffer copy = tmp.slice(); //Note: always BIG_ENDIAN!
                    copy.order(original.order());
                    return copy;
                }

                public ByteBuffer copy(final ByteBuffer original)
                {
                    final ByteBuffer tmp = original.duplicate();//Note: always BIG_ENDIAN!
                    tmp.position(0);
                    tmp.limit(original.position());
                    final ByteBuffer copy = ByteBuffer.allocate(tmp.remaining());
                    copy.put(tmp);
                    copy.flip();
                    copy.order(original.order());
                    return copy;
                }
            },
    /** Data content [0 - limit]. */
    ZERO_TO_LIMIT
            {
                public ByteBuffer normalize(final ByteBuffer original)
                {
                    final ByteBuffer tmp = original.duplicate();
                    tmp.position(0);
                    final ByteBuffer buffer = tmp.slice(); //Note: always BIG_ENDIAN!
                    buffer.order(original.order());
                    return buffer;
                }

                public ByteBuffer copy(final ByteBuffer original)
                {
                    final ByteBuffer tmp = original.duplicate();//Note: always BIG_ENDIAN!
                    tmp.position(0);
                    final ByteBuffer copy = ByteBuffer.allocate(tmp.remaining());
                    copy.put(tmp);
                    copy.flip();
                    copy.order(original.order());
                    return copy;
                }
            },
    /** Data content [0 - capacity]. */
    ZERO_TO_CAPACITY
            {
                public ByteBuffer normalize(final ByteBuffer original)
                {
                    final ByteBuffer buffer = original.duplicate(); //Note: always BIG_ENDIAN!
                    buffer.clear();
                    buffer.order(original.order());
                    return buffer;
                }

                public ByteBuffer copy(final ByteBuffer original)
                {
                    final ByteBuffer tmp = original.duplicate();//Note: always BIG_ENDIAN!
                    tmp.clear();
                    final ByteBuffer copy = ByteBuffer.allocate(tmp.remaining());
                    copy.put(tmp);
                    copy.flip();
                    copy.order(original.order());
                    return copy;
                }
            };

    /**
     * Produce a view of the buffer in "canonical" form with the active
     * data content starting at zero and spanning to capacity with
     * the position at zero and the limit at capacity.
     *
     * @param original The source buffer.
     * @return A normalized view of the buffer's backing store with the data
     *         content indexed at a zero-based position.
     *
     */
    public abstract ByteBuffer normalize(final ByteBuffer original);

    /**
     * Create a copy of the (normalized) data content of a buffer.
     *
     * @param original  The source buffer.
     * @return A copy of the original buffer's normalized backing store.
     */
    public abstract ByteBuffer copy(final ByteBuffer original);


    /**
     * Fills a gap in the ByteBuffer interface to realize a bulk transfer of
     * an arbitrary segment.
     *
     * Despite the temporary object creations, the transfer may be more
     * efficient by utilizing System.arrayCopy for the transfer.
     *
     * @param original  The source buffer.
     * @param dest The target array.
     * @param offset The starting index of the transfer in the target array.
     * @param start  The starting index of data, normalized to the data content.
     * @param length  The number of bytes to transfer.
     */
    public void get(final ByteBuffer original, final byte[] dest,
                    final int offset, final int start, final int length)
    {
        final ByteBuffer view = this.view(original, start, length);
        view.get(dest, offset, length);
    }

    /**
     * Utility method to produce an arbitrary slice of a buffer.
     *
     * The view indexes are interpreted based on the normalized form
     * of the buffer, i.e., relative to the start of the data defined
     * by the instance of this enumeration.
     *
     * @param original The source buffer.
     * @param start The index of the start of the view.
     * @param length The number of bytes in the view.
     * @return A view of the original buffer's backing store.
     */
    public ByteBuffer view(final ByteBuffer original, final int start,
                                  final int length)
    {
        ByteBuffer tmp = this.normalize(original);
        tmp.position(start);
        tmp.limit(start+length);
        ByteBuffer slice = tmp.slice();//Note: always BIG_ENDIAN!
        slice.order(original.order());
        return slice;
    }

    /**
     * Utility method to produce a copy of an arbitrary slice of a buffer.
     *
     * The view indexes are interpreted based on the normalized form
     * of the buffer, i.e., relative to the start of the data defined
     * by the instance of this enumeration.
     *
     * @param original The source buffer.
     * @param start The index of the start of the view.
     * @param length The number of bytes in the view.
     * @return A copy of the original content.
     */
    public ByteBuffer copy(final ByteBuffer original, final int start,
                           final int length)
    {
        ByteBuffer tmp = this.normalize(original);
        tmp.position(start);
        tmp.limit(start+length);
        return POSITION_TO_LIMIT.copy(tmp);
    }


    /**
     * A convenience method for the most common case of producing
     * an arbitrary view of a buffer with a zero-based index.
     *
     * @param original The source buffer.
     * @param start The index of the start of the view.
     * @param length The number of bytes in the view.
     * @return A view of the original buffer's backing store.
     */
    public static ByteBuffer viewAbsolute(ByteBuffer original, final int start,
                                  final int length)
    {
       return ZERO_TO_CAPACITY.view(original, start, length);
    }


}
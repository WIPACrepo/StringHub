package icecube.daq.performance.binary.test;


import java.util.Arrays;

/**
 * Provides ordered data and associated queries used to test classes that
 * provide the RangeSearch or Ordered interfaces.
 *
 * Its a pain to make up the test data so this supports reuse.
 */
public class OrderedDataCase
{
    private static long[] EMPTY = new long[0];

    private final long[] ordinals;
    private final QueryCase[] queries;

    public OrderedDataCase(final long[] ordinals,
                           final QueryCase[] queries)
    {
        this.ordinals = ordinals;
        this.queries = queries;
    }

    public long[] ordinals()
    {
        return Arrays.copyOf(ordinals, ordinals.length);
    }

    public QueryCase[] queries()
    {
        QueryCase[] copy = new QueryCase[queries.length];
        for (int i = 0; i < copy.length; i++)
        {
            copy[i]=queries[i].copy();
        }

        return copy;
    }


    // basic data sequence, including duplicates with
    // queries that test each type of edge case
    private static long[] STANDARD_DATA  = new long[]
            {
                    1000, 1000, 1001, 1002, 1003, 1999, 1999,
                    2000,       2001, 2002, 2003, 2999,
                    3000,       3001, 3002, 3003, 3999,
                    4000,       4001, 4002, 4003, 4999,
                    5000,       5001, 5002, 5003, 5999,
                    6000,       6001, 6002, 6003, 6999,

                    8000000000L, 8001000000L,             8002000000L,
                    9000000000L, 9001000000L, 9001000000L

            };
    private static  QueryCase[] STANDARD_QUERIES = new QueryCase[]
            {
                    // query ranges before data start
                    new QueryCase(0,0, EMPTY),
                    new QueryCase(0,999, EMPTY),
                    new QueryCase(999,999, EMPTY),
                    new QueryCase(997,998, EMPTY),
                    new QueryCase(Long.MIN_VALUE, Long.MIN_VALUE, EMPTY),
                    new QueryCase(Long.MIN_VALUE, 900, EMPTY),


                    // query ranges after data end
                    new QueryCase(9001000001L, 9001000001L, EMPTY),
                    new QueryCase(9001000001L, Long.MAX_VALUE, EMPTY),
                    new QueryCase(Long.MAX_VALUE, Long.MAX_VALUE, EMPTY),
                    new QueryCase(Long.MAX_VALUE-1000, Long.MAX_VALUE-900, EMPTY),

                    // query ranges straddling data start
                    new QueryCase(Long.MIN_VALUE,1000, new long[]{1000, 1000}),
                    new QueryCase(0,1000, new long[]{1000, 1000}),
                    new QueryCase(1000,1000, new long[]{1000, 1000}),
                    new QueryCase(0,1001, new long[]{1000, 1000, 1001}),
                    new QueryCase(1000,1001, new long[]{1000, 1000, 1001}),

                    //query ranges internal
                    new QueryCase(1001,1001, new long[]{1001}),
                    new QueryCase(1001,1999, new long[]{1001, 1002, 1003, 1999, 1999}),
                    new QueryCase(1001,2000, new long[]{1001, 1002, 1003, 1999, 1999, 2000}),

                    // query ranges straddling data end
                    new QueryCase(8000000000L, Long.MAX_VALUE,
                            new long[]{8000000000L, 8001000000L, 8002000000L,
                                    9000000000L, 9001000000L, 9001000000L}),
                    new QueryCase(8000000000L, 9001000000L,
                            new long[]{8000000000L, 8001000000L, 8002000000L,
                                    9000000000L, 9001000000L, 9001000000L}),
                    new QueryCase(9001000000L, Long.MAX_VALUE,
                            new long[]{9001000000L, 9001000000L}),
                    new QueryCase(9001000000L, 9001000000L,
                            new long[]{9001000000L, 9001000000L}),
                    new QueryCase(9001000000L, 9001000001L,
                            new long[]{9001000000L, 9001000000L}),

                    // query ranges covering entire test data range
                    new QueryCase(Long.MIN_VALUE, Long.MAX_VALUE, STANDARD_DATA),
                    new QueryCase(1000, 9001000000L, STANDARD_DATA),
                    new QueryCase(999, 9001000001L, STANDARD_DATA),
                    new QueryCase(0, 9001000001L, STANDARD_DATA),
                    new QueryCase(1000, Long.MAX_VALUE, STANDARD_DATA)

            };
    public static OrderedDataCase STANDARD_CASE =
            new OrderedDataCase( STANDARD_DATA, STANDARD_QUERIES);


    private static long[] EMPTY_DATA  = new long[0];
    private static  QueryCase[] EMPTY_QUERIES = new QueryCase[]
            {
                    new QueryCase(Long.MIN_VALUE, Long.MIN_VALUE, EMPTY),
                    new QueryCase(Long.MIN_VALUE, Long.MAX_VALUE, EMPTY),
                    new QueryCase(0, 0, EMPTY),
                    new QueryCase(Long.MIN_VALUE, 0, EMPTY),
                    new QueryCase(0, Long.MAX_VALUE, EMPTY),
                    new QueryCase(214137413, 3414523145L, EMPTY)
            };
    public static OrderedDataCase EMPTY_CASE =
            new OrderedDataCase( EMPTY_DATA, EMPTY_QUERIES);


    // holds test queries and expected results
    public static class QueryCase
    {
        public final long from;
        public final long to;
        public final long[] expected;

        QueryCase(final long from, final long to, final long[] expected)
        {
            this.from = from;
            this.to = to;
            this.expected = expected;
        }

        // support mutability isolation.
        private QueryCase copy()
        {
            return new QueryCase(from, to, Arrays.copyOf(expected,
                    expected.length));
        }
    }
}

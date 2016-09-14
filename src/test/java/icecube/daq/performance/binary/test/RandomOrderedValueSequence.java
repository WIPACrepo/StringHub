package icecube.daq.performance.binary.test;

/**
 * Utility to generate an ordered sequence of longs with randomly
 * varying intervals between values.
 */
public class RandomOrderedValueSequence
{
    final long startValue;
    final long maxStep;

    long lastValue;


    public RandomOrderedValueSequence()
    {
        this((long) (Math.random() * Integer.MAX_VALUE));
    }

    public RandomOrderedValueSequence(final long startValue)
    {
        this(startValue, 1000);
    }

    public RandomOrderedValueSequence(final long startValue,
                                      final long maxStep)
    {
        if(maxStep<1)
        {
            throw new IllegalArgumentException("Step must be > 0");
        }

        this.startValue = startValue;
        this.maxStep = maxStep;

        this.lastValue = startValue;
    }

    public long next()
    {
        long step = (long) (Math.random() * (maxStep+1));

        // arrange to always end at Long.MAX_VALUE
        if(Long.MAX_VALUE - step < lastValue)
        {
            if(lastValue != Long.MAX_VALUE)
            {
                lastValue = Long.MAX_VALUE;
                step = 0;
            }
            else
            {
                throw new Error("Sequence Exhausted");
            }
        }
        lastValue += step;

        return lastValue;
    }


    public static void main(String[] args)
    {
        RandomOrderedValueSequence seq =
                new RandomOrderedValueSequence(Long.MAX_VALUE - 100000, 1000);
        while(true)
        {
            long val = seq.next();
            System.out.println("seq = " + val +
                    " (" + (Long.MAX_VALUE - val) + ")");
        }
    }

}

package icecube.daq.util;

/**
 * Implement a simple moving average of long values.
 *
 * NOTE: Does not manage numeric overflow. If robust implementation
 * is required, consider importing the Apache Commons Math
 * library.
 *
 */
public class SimpleMovingAverage
{

    private final long[] samples;
    private int idx;
    private int validValues=0;

    private long sum;
    private double average;

    public SimpleMovingAverage(int window)
    {
        samples = new long[window];
        idx=0;

        average = 0d;
    }

    public final double add(final long value)
    {
        synchronized (this)
        {
            sum -= samples[idx];
            sum += value;
            samples[idx] = value;

            idx = ++idx % samples.length;

            if(validValues < samples.length)
            {
                validValues++;
            }

            average = (double)sum / (double)validValues;

            return average;
        }
    }

    public final double getAverage()
    {
        return average;
    }

}

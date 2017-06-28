package icecube.daq.rapcal;

/**
 * Maintains an exponential average of samples while filtering outlier
 * samples from the average.
 *
 * During the setup phase, outlier samples cause the average to be reset.
 * This ensures that the average is not established by an outlier value.
 */
public class ExponentialAverage
{
    private final double expWt;
    private final double threshold;
    private final int requiredSetupSamples;
    private double average;
    private int stableSampleCount;


    public ExponentialAverage(final double expWt, final double threshold,
                       final int requiredSetupSamples)
    {
        this.expWt = expWt;
        this.threshold = threshold;
        this.requiredSetupSamples = requiredSetupSamples;
        this.average = Double.NaN;
    }

    public double getAverage()
    {
        return average;
    }

    public boolean add(double sample)
    {
        if (Double.isNaN(average))
        {
            average = sample;
            stableSampleCount++;
            return true;
        }
        else if (Math.abs(average - sample) < threshold)
        {
            average = (average + expWt * sample) / (1.0 + expWt);
            stableSampleCount++;
            return true;
        }
        else
        {
            if(stableSampleCount < requiredSetupSamples)
            {
                // Assume the possibility that the initial samples
                // where outliers and reset the average
                average = sample;
                stableSampleCount = 1;
                return true;
            }
            else
            {
                return false;
            }
        }
    }

}

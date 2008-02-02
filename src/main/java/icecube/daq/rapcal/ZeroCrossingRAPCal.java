package icecube.daq.rapcal;

public class ZeroCrossingRAPCal extends AbstractRAPCal
{
    
    private final double threshold;
    private final int numBaseline;
    
    public ZeroCrossingRAPCal()
    {
        super();
        numBaseline = Integer.getInteger("icecube.daq.rapcal.ZeroCrossingRAPCal.baseline_samples", 20);
        threshold = Double.parseDouble(System.getProperty("icecube.daq.rapcal.LeadingEdgeRAPCal.threshold", "0.0"));
    }
    @Override
    double getFineTimeCorrection(short[] w) throws RAPCalException
    {
        double mean = 0.0;
        for (int i = 0; i < numBaseline; i++) mean += w[i];
        mean /= numBaseline;
        for (int i = 46; i > numBaseline; i--)
        {
            double a = w[i] - mean;
            double b = w[i+1] - mean;
            if (a > threshold && b <= threshold) return 50.0E-09 * (i+(threshold-a)/(b-a) - 48.0);
        }
        throw new RAPCalException(w);
    }

}

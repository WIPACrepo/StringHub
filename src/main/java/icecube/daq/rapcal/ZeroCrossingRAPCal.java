package icecube.daq.rapcal;

public class ZeroCrossingRAPCal extends AbstractRAPCal
{

    private final double threshold;

    public ZeroCrossingRAPCal()
    {
        super();
        threshold = Double.parseDouble(System.getProperty(
            "icecube.daq.rapcal.LeadingEdgeRAPCal.threshold", "0.0"));
    }
    @Override
    double getFineTimeCorrection(short[] w) throws RAPCalException
    {
        double mean = getBaseline(w);
        for (int i = 46; i > 30; i--) {
            double a = w[i] - mean;
            double b = w[i + 1] - mean;
            if (a > threshold && b <= threshold) {
                return 50.0E-09 * (i + (threshold - a) / (b - a) - 48.0);
            }
        }
        throw new RAPCalException(w);
    }

}

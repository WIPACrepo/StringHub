package icecube.daq.rapcal;

import org.apache.log4j.Logger;

/**
 * Quadratic edge fitter.  This class finds the LE threshold crossing
 * point as per LeadingEdgeRAPCal.  It then uses those bounding points
 * plus the next sample to fit a quadratic form and interpolates on the
 * quadratic roots.
 * @author kael
 *
 */
public class QuadFitLE extends AbstractRAPCal
{
    private final double threshold;
    private static final Logger logger = Logger.getLogger(QuadFitLE.class);
    
    public QuadFitLE()
    {
        super();
        threshold = Double.parseDouble
        (
                System.getProperty
                (
                        "icecube.daq.rapcal.LeadingEdgeRAPCal.threshold", 
                        "100.0"
                )
        );
    }
    
    @Override
    double getFineTimeCorrection(short[] w) throws RAPCalException
    {
        // compute mean of leading samples
        double mean = 0.0;
        for (int i = 0; i < 20; i++) mean += w[i];
        mean /= 20.0;
        
        // look for edge crossing
        for (int i = 10; i < 47; i++) {
            double a = w[i] - mean;
            double b = w[i+1] - mean;
            if (a < threshold && b >= threshold)
            {
                double c = w[i+2] - mean;
                return 50.0e-09 * (i + quadfit(a, b, c) - 48.0);
            }
        }
        throw new RAPCalException(w);
    }

    /**
     * Fit to quadratic - return abscissa of root in [0,1] whose
     * existence is guaranteed by construction 
     * @param a
     * @param b
     * @param c
     * @return
     * @throws RAPCalException
     */
    private double quadfit(double a, double b, double c) throws RAPCalException
    {
        final double C = a;
        final double A = 0.5*(c + a - 2.0*b);
        final double B = b - a - A;
        final double discriminant = B*B - 4.0*A*(C-threshold);
        final double rd = Math.sqrt(discriminant);
        final double r1 = -(B + rd) / (2.0*A);
        final double r2 = -(B - rd) / (2.0*A);
        if (logger.isDebugEnabled())
        {
            logger.debug("r1: " + r1 + " - r2: " + r2 + " A, B, C = (" + 
                    A + ", " + B + ", " + C + ")");
        }
        if (r1 >= 0.0 && r1 <= 1.0) return r1;
        return r2;
    }
}

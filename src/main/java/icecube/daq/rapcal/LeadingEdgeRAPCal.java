package icecube.daq.rapcal;

import org.apache.log4j.Logger;

public class LeadingEdgeRAPCal extends AbstractRAPCal
{
    private final double threshold;
    private static final Logger logger = Logger.getLogger(LeadingEdgeRAPCal.class);
    
    public LeadingEdgeRAPCal()
    {
        super();
        threshold = Double.parseDouble(System.getProperty("icecube.daq.rapcal.LeadingEdgeRAPCal.threshold", "100.0"));
    }
    
    /**
     * Calculate fine time correction to TCAL rough timestamp by using interpolated
     * leading edge crossing of a threshold. 
     * @param w input RAPCal waveform
     * @return leading edge correction to the RAPCal waveform in units of seconds.
     * @throws RAPCalException when something goes wrong with the edge detection
     */
    @Override
    double getFineTimeCorrection(short[] w) throws RAPCalException
    {
        // compute mean of leading samples
        double mean = getBaseline(w);
        
        // look for edge crossing
        for (int i = 10; i < 47; i++) {
            double a = w[i] - mean;
            double b = w[i+1] - mean;
            if (a < threshold && b >= threshold) 
                return 50.0e-09 * (i + (threshold-a) / (b-a) - 48.0);
        }
        
        StringBuffer txt = new StringBuffer();
        txt.append("Past the end of TCAL vector:");
        for (int i = 0; i < 48; i++) txt.append(" " + w[i]);
        logger.warn(txt);
        throw new RAPCalException(w);
    }
    
}

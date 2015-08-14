package icecube.daq.rapcal;

public class CFDiscLE extends AbstractRAPCal
{
    private static final double fthr = 0.5;

    @Override
    double getFineTimeCorrection(short[] w) throws RAPCalException
    {
        double baseline = getBaseline(w);
        double[] wps = new double[20];
        double wmax = 0.0;
        for (int i = 0; i < 20; i++)
        {
            wps[i] = w[i+20] - baseline;
            if (wps[i] > wmax) wmax = wps[i];
        }

        double f0 = wps[0] / wmax;
        for (int i = 0; i < 20; i++)
        {
            double f1 = wps[i] / wmax;
            if (f0 <= fthr && f1 > fthr)
            {
                double dx = (fthr - f0) / (f1 - f0);
                return 50.0E-09*((i - 28) + dx);
            }
            f0 = f1;
        }
        throw new BadTCalException(getClass().getName(), w);
    }

}

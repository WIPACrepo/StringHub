/**
 * TCALTest is a test fixture for TCALs.
 */
package icecube.daq.dor;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import icecube.daq.rapcal.LeadingEdgeRAPCal;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.util.UTC;

public class TCALTest
{
    private int card;
    private int pair;
    private char dom;
    private Driver driver = Driver.getInstance();
    private RAPCal rapcal;
    
    private TCALTest(int card, int pair, char dom, double thresh)
    {
        this.card = card;
        this.pair = pair;
        this.dom  = dom;
        rapcal  = new LeadingEdgeRAPCal(thresh);
    }

    private void run(int n) throws Exception
    {
        ArrayList<Double> cableLengthList = new ArrayList<Double>();
        UTC u0 = new UTC();
        rapcal.update(driver.readTCAL(card, pair, dom), u0);
        for (int iter = 0; iter < n; iter++)
        {
            TimeCalib tcal = driver.readTCAL(card, pair, dom);
            rapcal.update(tcal, u0);
            System.out.println(
                    rapcal.cableLength() + " " +
                    rapcal.clockRatio());
            cableLengthList.add(rapcal.cableLength());
        }
        double mean = 0.0;
        for (double x : cableLengthList) mean += x;
        mean /= cableLengthList.size();
        double var  = 0.0;
        for (double x : cableLengthList) 
        {
            double svar = x - mean;
            var += svar * svar;
        }
        var /= cableLengthList.size();
        System.out.format("CLEN: %.1f +/- %.1f\n", mean * 1.0E+09, Math.sqrt(var) * 1.0E+09);
    }
    
    public static void main(String[] args) throws Exception
    {
        double threshold = 50.0;
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
        if (args.length < 2)
        {
            System.err.println("usage - java icecube.daq.dor.TCALTest [opts] [cwd] [# iter]");
            System.exit(1);
        }
        int iarg = 0;
        while (args[iarg].charAt(0) == '-')
        {
            String opt = args[iarg++].substring(1);
            if (opt.equals("thresh"))
            {
                threshold = Double.parseDouble(args[iarg++]);
            }
        }
        
        int card = Integer.parseInt(args[iarg].substring(0, 1));
        int pair = Integer.parseInt(args[iarg].substring(1, 2));
        char dom = args[iarg++].substring(2).toUpperCase().charAt(0);
        int nIter = Integer.parseInt(args[iarg++]);
        
        TCALTest test = new TCALTest(card, pair, dom, threshold);
        
        test.run(nIter);
    }
}

/**
 * TCALTest is a test fixture for TCALs.
 */
package icecube.daq.dor;

import icecube.daq.rapcal.RAPCal;
import icecube.daq.util.UTC;

import java.util.ArrayList;
import java.io.File;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public final class TCALTest
{
    private Driver driver = Driver.getInstance();
    private RAPCal rapcal;
    private File tcalFile;

    private TCALTest(int card, int pair, char dom, String classname) throws Exception
    {
        rapcal = (RAPCal) Class.forName(classname).newInstance();
	tcalFile = driver.getTCALFile(card, pair, dom);
    }

    private void run(int n) throws Exception
    {
        ArrayList<Double> cableLengthList = new ArrayList<Double>();
        UTC u0 = new UTC();
        rapcal.update(driver.readTCAL(tcalFile), u0);
        for (int iter = 0; iter < n; iter++)
        {
            TimeCalib tcal = driver.readTCAL(tcalFile);
            rapcal.update(tcal, u0);
            System.out.println(
                    rapcal.cableLength() + " " +
                    rapcal.clockRatio());
            cableLengthList.add(rapcal.cableLength());
            Thread.sleep(500L);
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
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);

        if (args.length < 2)
        {
            System.err.println("usage - java icecube.daq.dor.TCALTest <cwd> <# iter>");
            System.exit(1);
        }

        int iarg = 0;
        String classname = "icecube.daq.rapcal.LeadingEdgeRAPCal";
        while (iarg < args.length && args[iarg].charAt(0) == '-')
        {
            String opt = args[iarg++].substring(1);
            if (opt.equals("debug")) Logger.getRootLogger().setLevel(Level.DEBUG);
            if (opt.equals("classname")) classname = args[iarg++];
        }

        int card = Integer.parseInt(args[iarg].substring(0, 1));
        int pair = Integer.parseInt(args[iarg].substring(1, 2));
        char dom = args[iarg++].substring(2).toUpperCase().charAt(0);
        int nIter = Integer.parseInt(args[iarg++]);

        TCALTest test = new TCALTest(card, pair, dom, classname);

        test.run(nIter);
    }
}

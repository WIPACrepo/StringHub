package icecube.daq.dor;

import java.io.IOException;

import icecube.daq.rapcal.LeadingEdgeRAPCal;
import icecube.daq.rapcal.RAPCal;

public class TCALTest
{
    private int card;
    private int pair;
    private char dom;
    private Driver driver = Driver.getInstance();
    private RAPCal rapcal;
    
    private TCALTest(int card, int pair, char dom)
    {
        this.card = card;
        this.pair = pair;
        this.dom  = dom;
        rapcal  = new LeadingEdgeRAPCal(50.0);
    }

    private void run(int n) throws Exception
    {
        rapcal.update(driver.readTCAL(card, pair, dom), driver.readGPS(card).getOffset());
        for (int iter = 0; iter < n; iter++)
        {
            GPSInfo gps = driver.readGPS(card);
            TimeCalib tcal = driver.readTCAL(card, pair, dom);
            rapcal.update(tcal, gps.getOffset());
            System.out.println("T:" + 
                    " " + tcal.getDorTx() + " " + gps.getOffset() + 
                    " " + rapcal.cableLength() + 
                    " " + rapcal.clockRatio());
        }

    }
    public static void main(String[] args) throws Exception
    {
        if (args.length < 2)
        {
            System.err.println("usage - java icecube.daq.dor.TCALTest [cwd] [# iter]");
            System.exit(1);
        }
        int card = Integer.parseInt(args[0].substring(0, 1));
        int pair = Integer.parseInt(args[0].substring(1, 2));
        char dom = args[0].substring(2).toUpperCase().charAt(0);
        int nIter = Integer.parseInt(args[1]);
        
        TCALTest test = new TCALTest(card, pair, dom);
        
        test.run(nIter);
    }
}

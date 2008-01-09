package icecube.daq.dor;

import java.io.IOException;
import java.util.HashMap;

public interface IDriver
{
    GPSInfo readGPS(int card)
        throws GPSException;

    TimeCalib readTCAL(int card, int pair, char dom)
        throws IOException, InterruptedException;

    void softboot(int card, int pair, char dom) throws IOException;
    
    void commReset(int card, int pair, char dom) throws IOException;
    
    void setBlocking(boolean block) throws IOException;
    
    HashMap<String, Integer> getFPGARegisters(int card) throws IOException;

    public void resetComstat(int card, int pair, char dom) throws IOException;

    public String getComstat(int card, int pair, char dom) throws IOException;

    public String getFPGARegs(int card) throws IOException;

}

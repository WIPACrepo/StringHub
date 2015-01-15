package icecube.daq.dor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public interface IDriver
{
    File getTCALFile(int card, int pair, char dom);
    File getGPSFile(int card);

    GPSInfo readGPS(File gpsFile)
        throws GPSException;

    TimeCalib readTCAL(File tcalFile)
        throws IOException, InterruptedException;

    void softboot(int card, int pair, char dom) throws IOException;

    void commReset(int card, int pair, char dom) throws IOException;

    void setBlocking(boolean block) throws IOException;

    HashMap<String, Integer> getFPGARegisters(int card) throws IOException;

    void resetComstat(int card, int pair, char dom) throws IOException;

    String getComstat(int card, int pair, char dom) throws IOException;

    String getFPGARegs(int card) throws IOException;
    
    String getProcfileID(int card, int pair, char dom) throws IOException;

}

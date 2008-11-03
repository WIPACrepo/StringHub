package icecube.daq.domapp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

public class IcebootInterface extends DOMIO
{
    private static final Logger logger = Logger.getLogger(IcebootInterface.class);
    
    public IcebootInterface(int card, int pair, char dom) throws FileNotFoundException
    {
        super(card, pair, dom);
    }
    
    public String sendCommand(String cmd) throws IOException
    {
        return sendCommand(cmd, "> \n");
    }
    
    /**
     * Higher-level interface to sending iceboot commands and returning any data
     * beyond the command echo.
     * @param cmd
     * @param expect
     * @return 
     * @throws IOException
     */
    public String sendCommand(String cmd, String expect) throws IOException
    {
        ByteBuffer buf = ByteBuffer.allocate(256);
        buf.put(cmd.getBytes());
        buf.put("\r\n".getBytes()).flip();
        send(buf);
        if (logger.isDebugEnabled()) logger.debug("Sending: " + cmd);
        while (true)
        {
            ByteBuffer ret = recv();
            byte[] bytearray = new byte[ret.remaining()];
            ret.get(bytearray);
            String fragment = new String(bytearray);
            if (logger.isDebugEnabled()) logger.debug("Received: " + fragment);
            if (fragment.contains(cmd)) break;
        }
        if (expect == null) return "";
        if (logger.isDebugEnabled()) logger.debug("Echoback from iceboot received - expecting ... " + expect);
        StringBuffer txt = new StringBuffer();
        while (true)
        {
            ByteBuffer ret = recv();
            byte[] bytearray = new byte[ret.remaining()];
            ret.get(bytearray);
            String fragment = new String(bytearray);
            if (fragment.contains(expect)) break;
            txt.append(fragment);
        }
        return txt.toString().trim();
    }
    
    public String getMainboardId() throws IOException
    {
        return sendCommand("domid type");
    }
    
    public void powerOnFlasherboard() throws IOException
    {
        sendCommand("enableFBminY");
    }
    
    public void powerOffFlasherboard() throws IOException
    {
        sendCommand("disableFB");
    }
}

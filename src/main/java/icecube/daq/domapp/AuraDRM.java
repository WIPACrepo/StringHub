package icecube.daq.domapp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

public class AuraDRM extends IcebootInterface
{
    private static final Logger logger = Logger.getLogger(AuraDRM.class);
    
    public AuraDRM(int card, int pair, char dom) throws FileNotFoundException
    {
        super(card, pair, dom);
    }
    
    public ByteBuffer forcedTrig(int n) throws IOException
    {
        return readTRACRData(n + " forceTrig", n*4854);
    }
    
    private ByteBuffer readTRACRData(String cmd, int bufsize) throws IOException
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
        ByteBuffer data = ByteBuffer.allocate(bufsize);
        while (data.remaining() > 0) 
        {
            ByteBuffer ret = recv();
            data.put(ret);
        }
        return data;
    }
}

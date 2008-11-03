package icecube.daq.domapp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

public class AuraDRM extends IcebootInterface
{
    private static final int FL_BASE = 0x60000000;
    private static final int VIRT_HI = FL_BASE + 0x21;
    private static final int VIRT_LO = FL_BASE + 0x22;
    private static final int VIRT_RW = FL_BASE + 0x23;
    private static final int FIFO_RD = FL_BASE + 0x25;
    private static final int[] dacMap = new int[] { 
        0x9A, 0x9E, 0xA2, 0xA6,         // antenna 0 : bands 0 - 3 
        0x98, 0x9C, 0xA0, 0xA4,         // antenna 1 : bands 0 - 3
        0xAA, 0xAE, 0xB2, 0xB6,         // antenna 2 : bands 0 - 3
        0xA8, 0xAC, 0xB0, 0xB4          // antenna 3 : bands 0 - 3
    };
    private static final Logger logger = Logger.getLogger(AuraDRM.class);
    
    public AuraDRM(int card, int pair, char dom) throws FileNotFoundException
    {
        super(card, pair, dom);
    }
    
    public ByteBuffer forcedTrig(int n) throws IOException
    {
        return readTRACRData(n + " forcedtrig", n*4854 + 3);
    }
    
    public int readVirtualAddress(int command) throws IOException
    {
        return Integer.parseInt(
                sendCommand(String.format("$%x $%x c! $%x c@ . drop", command, VIRT_HI, VIRT_RW))
                );
    }
    
    public void writeVirtualAddress(int command, int val) throws IOException
    {
        int c_hi = (command >> 8) & 0xff;
        int c_lo = command & 0xff;
        sendCommand(String.format(
                "$%x $%x c! $%x $%x c! $%x $%x c!", 
                c_hi, VIRT_HI,
                c_lo, VIRT_LO,
                val, VIRT_RW));
    }
    
    /**
     * This command queues up the radio DACs for a write operation.  To actually write
     * the DACs follow up with writeRadioDACs() 
     * @param ant
     * @param band
     * @param val
     * @throws IOException
     */
    public void setRadioDAC(int ant, int band, int val) throws IOException
    {
        int ich = 4 * (ant - 1) + (band - 1);
        int cmd = dacMap[ich];
        writeVirtualAddress(cmd, val & 0xff);
        writeVirtualAddress(cmd+1, (val >> 8) & 0xff);
    }
    
    public void writeRadioDACs() throws IOException
    {
        writeVirtualAddress(0xB8, 0x01);
        try
        {
            do
            {
                Thread.sleep(10L);
            } 
            while (readVirtualAddress(0xB9) == 1);
        }
        catch (InterruptedException intx)
        {
            // Do nothing
        }
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
        // Ensure that the prompt has been seen in the last 3 bytes of the buffer - then ignore it
        assert data.get(bufsize - 3) == '>' && 
            data.get(bufsize - 2) == ' ' && 
            data.get(bufsize - 1) == '\n';
        return (ByteBuffer) data.rewind().limit(bufsize - 3);
    }
}

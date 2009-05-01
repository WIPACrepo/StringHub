package icecube.daq.domapp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

public class AuraDRM extends IcebootInterface
{
    private static final int FL_BASE        = 0x60000000;
    @SuppressWarnings("unused")
    private static final int TRACR_STATUS   = FL_BASE + 0x10; 
    @SuppressWarnings("unused")
    private static final int FIFO_READ      = FL_BASE + 0x25;
    private static final int FIFO_RESET     = FL_BASE + 0x29;
    private static final int VIRT_HI        = FL_BASE + 0x21;
    private static final int VIRT_LO        = FL_BASE + 0x22;
    private static final int VIRT_RW        = FL_BASE + 0x23;
    private static final int MAX_POWER_TRY  = 100;
    private static final int PWR_BITS       = 6;

    private static final int MAX_EVT_SIZE   = 5000;

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
    
    public ByteBuffer radioTrig(int n) throws IOException
    {
        return readTRACRData(n + " radiotrig", n);
    }
    
    public ByteBuffer forcedTrig(int n) throws IOException
    {
        return readTRACRData(n + " forcedtrig", n);
    }
    
    /**
     * Obtain the TRACR - DOM mainboard clock offset using
     * Hagar's <b>Toffset</b> iceboot function.  This offset
     * is to be used to turn the 20 MHz TRACR clock into the
     * equivalent 40 MHz DOM mainboard clock so that the
     * standard RAPCal transformation can be used:<br/>
     *  UTC = 2*TRACR + Toffset<br/>
     * The method used contains inherent ambiguity because
     * of random latencies - it returns the <em>minimum</em>
     * of the population in an attempt to mitigate.
     * @param n the number of iterations to use
     * @return 
     * @throws IOException
     */
    public long getTRACRClockOffset(int n) throws IOException
    {
        String txt = sendCommand(n + " Toffset");
        return Long.parseLong(txt);
    }
    
    public int readVirtualAddress(int command) throws IOException
    {
        int c_hi = (command >> 8) & 0xff;
        int c_lo = command & 0xff;
        return Integer.parseInt(
                sendCommand(String.format(
                        "$%x $%x c! $%x $%x c! $%x c@ . drop", 
                        c_hi, VIRT_HI,
                        c_lo, VIRT_LO,
                        VIRT_RW))
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
     * 
     * @param pwr
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean writePowerBits(int pwr) throws IOException, InterruptedException
    {
        // Ramp power on by turning on a bit at a time
        int pwrMask = 0x1;
        for (int i = 0; i < PWR_BITS; i++) {
            writeVirtualAddress(4, pwr & pwrMask);
            pwrMask = (pwrMask << 1) | 0x1;
            Thread.sleep(200);
        }
        int powerTry = 0;
        while (readVirtualAddress(4) != pwr && powerTry++ < MAX_POWER_TRY) Thread.sleep(100);
        
        if (powerTry <= MAX_POWER_TRY) return true;
        logger.warn("Never got good power state.");
        return false;
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
        logger.debug("Setting DAC ("+ant+","+band+") to "+val);
        int ich = (ant << 2) | band;
        int cmd = dacMap[ich];
        writeVirtualAddress(cmd+1, val & 0xff);
        writeVirtualAddress(cmd, (val >> 8) & 0xff);
    }
    
    /**
     * This command actually effects the write for the DACs set by the setRadioDAC
     * call which must have been previously called one or more times.
     * 
     */
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
    
    private ByteBuffer readTRACRData(String cmd, int nEvents) throws IOException
    {
        sendCommand(cmd, null);
        ByteBuffer data = ByteBuffer.allocate(nEvents*MAX_EVT_SIZE+3);
        boolean finished = false;
        int p = 0;
        while ((data.remaining() > 0) && (!finished))
        {
            ByteBuffer ret = recv();
            data.put(ret);
            // Look for trailing 0xbeef + prompt
            p = data.position();
            if ((p > 5) &&
                (data.get(p - 5) == (byte)0xbe) &&
                (data.get(p - 4) == (byte)0xef) &&
                (data.get(p - 3) == '>') && 
                (data.get(p - 2) == ' ') && 
                (data.get(p - 1) == '\n')) {
                logger.debug("Found 0xbeef + prompt at position "+p+".");
                finished = true;
            }
        }
        p = data.position();
        if (logger.isDebugEnabled()) logger.debug("Got ByteBuffer("+p+" bytes) from DOM.");
        return (ByteBuffer) data.rewind().limit(p-3);
    }
    
    public void resetTRACRFifo() throws IOException
    {
        sendCommand(String.format("1 $%x c!", FIFO_RESET));
    }
}

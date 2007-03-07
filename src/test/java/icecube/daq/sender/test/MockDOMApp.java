package icecube.daq.sender.test;

import icecube.daq.domapp.EngineeringRecordFormat;
import icecube.daq.domapp.IDOMApp;
import icecube.daq.domapp.LocalCoincidenceConfiguration;
import icecube.daq.domapp.MessageException;
import icecube.daq.domapp.MuxState;
import icecube.daq.domapp.TriggerMode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

class MockDOMApp
    implements IDOMApp
{
    private long mbId;
    private String mbIdStr;
    private DOMHitGenerator hitGen;
    private short pulserRate;
    private int scalarDeadtime;

    MockDOMApp(long mbId, double maxTime, double rate)
    {
        this.mbId = mbId;

        hitGen = new DOMHitGenerator(mbId, maxTime, rate);
    }

    public void beginFlasherRun(short brightness, short width, short delay, 
                                short mask, short rate)
	throws MessageException
    {
        // do nothing
    }

    /**
     * Begin data collection on DOM.
     * @throws MessageException
     */
    public void beginRun()
        throws MessageException
    {
        // do nothing
    }

    public void close()
    {
        // do nothing
    }

    public void collectPedestals(int nAtwd0, int nAtwd1, int nFadc)
        throws MessageException
    {
        // do nothing
    }

    public void disableHV()
        throws MessageException
    {
        // do nothing
    }

    public void disableSupernova()
        throws MessageException
    {
        // do nothing
    }

    public void enableHV()
        throws MessageException
    {
        // do nothing
    }

    public void enableSupernova(int deadtime, boolean speDisc)
        throws MessageException
    {
        // do nothing
    }

    /**
     * Terminate data collection on DOM.
     * @throws MessageException
     */
    public void endRun()
        throws MessageException
    {
        // do nothing
    }

    public ByteBuffer getData()
        throws MessageException
    {
        ByteBuffer buf = ByteBuffer.allocate(32);
        hitGen.putHit(buf);
        buf.flip();
        return buf;
    }

    public String getMainboardID()
        throws MessageException
    {
        if (mbIdStr == null) {
            StringBuffer buf = new StringBuffer(Long.toHexString(mbId));
            while (buf.length() < 8) {
                buf.insert(0, '0');
            }

            mbIdStr = buf.toString();
        }

        return mbIdStr;
    }

    public ByteBuffer getMoni()
        throws MessageException
    {
        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.flip();
        return buf;
    }

    public short getPulserRate()
        throws MessageException
    {
        return pulserRate;
    }

    public String getRelease()
        throws MessageException
    {

        return "MockDOMApp 1.0";
    }

    public int getScalerDeadtime()
        throws MessageException
    {
        return scalarDeadtime;
    }

    public ByteBuffer getSupernova()
        throws MessageException
    {
        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.flip();
        return buf;
    }

    public boolean hasMoreData()
    {
        return hitGen.hasMoreData();
    }

    public void pulserOff()
        throws MessageException
    {
        // do nothing
    }

    public void pulserOn()
        throws MessageException
    {
        // do nothing
    }

    public short[] queryHV()
        throws MessageException
    {
        return null;
    }

    public void setCableLengths(short[] up, short[] dn)
        throws MessageException
    {
        // do nothing
    }

    public void setDeltaCompressionFormat()
        throws MessageException
    {
        // do nothing
    }

    public void setEngineeringFormat(EngineeringRecordFormat fmt)
        throws MessageException
    {
        // do nothing
    }

    public void setHV(short dac)
        throws MessageException
    {
        // do nothing
    }

    public void setLCMode(LocalCoincidenceConfiguration.RxMode mode)
        throws MessageException
    {
        // do nothing
    }

    public void setLCSource(LocalCoincidenceConfiguration.Source src)
        throws MessageException
    {
        // do nothing
    }
	
    public void setLCSpan(byte span) throws MessageException
    {
        // do nothing
    }

    public void setLCTx(LocalCoincidenceConfiguration.TxMode mode)
        throws MessageException
    {
        // do nothing
    }

    public void setLCType(LocalCoincidenceConfiguration.Type type)
        throws MessageException
    {
        // do nothing
    }

    public void setLCWindow(int pre, int post)
        throws MessageException
    {
        // do nothing
    }

    public void setMoniIntervals(int hw, int config)
        throws MessageException
    {
        // do nothing
    }

    public void setMux(MuxState mode)
        throws MessageException
    {
        // do nothing
    }

    public void setPulserRate(short rate)
        throws MessageException
    {
        // do nothing
    }

    public void setScalerDeadtime(int deadtime)
    {
        scalarDeadtime = deadtime;
    }

    public void setTriggerMode(TriggerMode mode)
        throws MessageException
    {
        // do nothing
    }

    public boolean transitionToDOMApp()
        throws IOException, InterruptedException
    {
        return true;
    }

    public void writeDAC(byte dac, short val)
        throws MessageException
    {
        // do nothing
    }

    public String toString()
    {
        return "MockDOMApp[" + mbId + "]";
    }

	public ArrayList<ByteBuffer> getData(int n) throws MessageException {
		// TODO Auto-generated method stub
		return new ArrayList<ByteBuffer>();
	}
}

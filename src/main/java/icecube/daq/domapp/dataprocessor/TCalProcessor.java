package icecube.daq.domapp.dataprocessor;

import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.domapp.RunLevel;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.TimeCalib;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Processes TCAL messages from a DOMApp data stream.
 *
 * The TCAL processor is unusual in that:
 *
 *    It updates the RAPCAL instance used for all streams in the processor.
 *
 *    It is active outside of the RUNNING state of acquisition, and therefore
 *    supports processing tcals without dispatching to a consumer.
 *
 *    It must manage the processing to the initial time calibrations without
 *    utilizing the RAPCAL instance for UTC reconstruction.
 *
 * These requirements are implemented via a state pattern design.
 */
class TCalProcessor implements DataProcessor.StreamProcessor
{

    private static Logger logger = Logger.getLogger(TCalProcessor.class);

    private final DataDispatcher dispatcher;
    private final long mbid;
    private final RAPCal rapcal;

    /** The GPS source.*/
    private final GPSProvider gpsProvider;

    interface ProcessingBehavior
    {
        long process(final TimeCalib tcal, final GPSInfo gps)
                throws IOException;
    }


    interface DispatchingBehavior
    {
        void dispatch(final TimeCalib tcal, final GPSInfo gps)
                throws IOException;
    }

    private ProcessingBehavior processingState;
    private DispatchingBehavior dispatchState;


    private final ProcessingBehavior INITIAL_PROCESSOR =
            new PrimordialTCalProcessor();

    private final ProcessingBehavior ESTABLISHED_PROCESSOR =
            new EstablishedCALProcessor();

    private final DispatchingBehavior RUNNING_DISPATCHER =
            new RunModeDispatcher();

    private final DispatchingBehavior NULL_DISPATCH =
            new DevNulDispatch();

    TCalProcessor(final DataDispatcher dispatcher,
                  final long mbid,
                  final RAPCal rapcal,
                  final GPSProvider gpsProvider)
    {
        this.dispatcher = dispatcher;
        this.mbid = mbid;
        this.rapcal = rapcal;
        this.gpsProvider = gpsProvider;

        this.processingState = INITIAL_PROCESSOR;
        this.dispatchState = NULL_DISPATCH;
    }

    @Override
    public void process(final ByteBuffer data, final DataStats counters)
            throws IOException
    {
        TimeCalib tcal = new TimeCalib(data);
        GPSInfo gps = gpsProvider.getGPSInfo();

        long utc = processingState.process(tcal, gps);
        dispatchState.dispatch(tcal, gps);

        //report
        counters.reportTCAL(utc);
    }

    @Override
    public void eos() throws IOException
    {
        dispatcher.eos(MultiChannelMergeSort.eos(mbid));
    }

    @Override
    public void runLevel(final RunLevel runLevel)
    {
        if(RunLevel.RUNNING.equals(runLevel))
        {
            dispatchState = RUNNING_DISPATCHER;
            logger.debug("Setting TCAL dispatching mode to [RUNNING]");

        }
        else
        {
            logger.debug("Setting TCAL dispatching mode to [NULL]");
            dispatchState = NULL_DISPATCH;
        }
    }

    private void setProcessingState(ProcessingBehavior state)
    {
        logger.debug("Setting TCAL processing mode to [" + state.getClass().getName() + "]");
        processingState = state;

    }


    private boolean updateRapCal(TimeCalib tcal, GPSInfo gps)
    {
        try
        {
            rapcal.update(tcal, gps.getOffset());
            return true;
        }
        catch (RAPCalException rcex)
        {
            //Note: Rapcal exceptions are logged and suppressed.
            //      to allow for the occasional bad rapcal
            logger.warn("Got RAPCal exception", rcex);
            return false;
        }
    }



    /**
     * Holds the initial system behavior until two successful
     * rapcal updates have been processed.
     *
     * UTC reconstruction requires these two tcal measurements
     */
    private class PrimordialTCalProcessor implements ProcessingBehavior
    {

        private int numRAPCalUpdates;

        @Override
        public long process(final TimeCalib tcal, final GPSInfo gps)
                throws IOException
        {
            if(gps != null)
            {
                boolean success = updateRapCal(tcal, gps);
                if(success)
                {
                    numRAPCalUpdates++;
                }
            }

            //Switch to normal processing mode once RAPCal has
            // been established.
            if(numRAPCalUpdates > 1)
            {
                TCalProcessor.this.setProcessingState(ESTABLISHED_PROCESSOR);
                return rapcal.domToUTC(tcal.getDomTx().in_0_1ns() / 250L).in_0_1ns();
            }
            else
            {
                // Note: Can not utilize rapcal for utc reconstruction yet.
                return -1;
            }
        }

    }


    /**
     * Holds the primary TCAL processing behavior
     */
    private class EstablishedCALProcessor implements ProcessingBehavior
    {


        @Override
        public long process(final TimeCalib tcal, final GPSInfo gps)
                throws IOException
        {
            if(gps != null)
            {
                updateRapCal(tcal, gps);
            }

            return rapcal.domToUTC(tcal.getDomTx().in_0_1ns() / 250L).in_0_1ns();

        }
    }


    /**
     * Non-Dispactching for use in run levels other than RUNNING
     */
    private class DevNulDispatch implements DispatchingBehavior
    {
        @Override
        public void dispatch(final TimeCalib tcal, final GPSInfo gps)
                throws IOException
        {
            //noop
        }
    }


    /**
     * RUNNING mode dispatcher.
     */
    private class RunModeDispatcher implements DispatchingBehavior
    {

        /**
         * Gernerate the daq formatted record and forward to the dispatcher.
         *
         */
        public void dispatch(final TimeCalib tcal, final GPSInfo gps)
                throws IOException
        {
            //generate and dispatch the daq formatted record
            if (!dispatcher.hasConsumer()) return;
            ByteBuffer tcalBuffer = ByteBuffer.allocate(500);
            tcalBuffer.putInt(0).putInt(DataProcessor.MAGIC_TCAL_FMTID);
            tcalBuffer.putLong(mbid);
            tcalBuffer.putLong(0L);
            tcalBuffer.putLong(tcal.getDomTx().in_0_1ns() / 250L);
            tcal.writeUncompressedRecord(tcalBuffer);

            //todo this state might should be prohibited, trace the consumers
            // of this to see how this data is utilized.  If gps is not
            // available, we may not be dispatching at all.
            if (gps == null)
            {
                // Set this to the equivalent of 0 time in GPS
                tcalBuffer.put("\001001:00:00:00 \000\000\000\000\000\000\000\000".getBytes());
            }
            else
            {
                tcalBuffer.put(gps.getBuffer());
            }
            tcalBuffer.flip();
            tcalBuffer.putInt(0, tcalBuffer.remaining());


            dispatcher.dispatchBuffer(tcalBuffer);

        }
    }
}
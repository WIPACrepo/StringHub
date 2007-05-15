package icecube.daq.sender;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.common.DAQCmdInterface;

import icecube.daq.eventbuilder.impl.ReadoutDataPayloadFactory;

import icecube.daq.monitoring.SenderMonitor;
import icecube.daq.payload.IDOMID;
import icecube.daq.payload.IDomHit;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.IPayloadDestinationCollection;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.payload.PayloadDestination;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;

import icecube.daq.payload.impl.DomHitEngineeringFormatPayload;

import icecube.daq.payload.splicer.Payload;

import icecube.daq.reqFiller.RequestFiller;

import icecube.daq.splicer.Spliceable;

import icecube.daq.trigger.IHitPayload;
import icecube.daq.trigger.IReadoutRequest;
import icecube.daq.trigger.IReadoutRequestElement;
import icecube.daq.trigger.impl.EngineeringFormatHitDataPayload;
import icecube.daq.trigger.impl.EngineeringFormatHitDataPayloadFactory;
import icecube.daq.trigger.impl.HitPayloadFactory;
import icecube.daq.trigger.impl.ReadoutRequestPayloadFactory;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import java.util.zip.DataFormatException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class TinyHitPayload
    implements IDomHit, ILoadablePayload
{
    private static Log LOG = LogFactory.getLog(Sender.class);

    private ByteBuffer byteBuf;
    private long time;
    private long domId;

    TinyHitPayload(ByteBuffer buf)
        throws DataFormatException, IOException
    {
        super();

        byteBuf = buf;

        domId = byteBuf.getLong(8);
        time = byteBuf.getLong(24);
    }

    public Object deepCopy()
    {
        throw new Error("Unimplemented");
    }

    public long getDomId()
    {
        return domId;
    }

    public ByteBuffer getPayloadBacking()
    {
        return byteBuf;
    }

    public int getPayloadInterfaceType()
    {
        throw new Error("Unimplemented");
    }

    public int getPayloadLength()
    {
        throw new Error("Unimplemented");
    }

    public IUTCTime getPayloadTimeUTC()
    {
        throw new Error("Unimplemented");
    }

    public int getPayloadType()
    {
        throw new Error("Unimplemented");
    }

    public long getTimestamp()
    {
        return time;
    }

    public int getTriggerMode()
    {
        throw new Error("Unimplemented");
    }

    public void loadPayload()
    {
        // ignore
    }

    public void recycle()
    {
        byteBuf = null;
    }

    public int writePayload(int iOffset, ByteBuffer tBuffer)
        throws IOException
    {
        throw new Error("Unimplemented");
    }

    public int writePayload(PayloadDestination tDestination)
        throws IOException
    {
        throw new Error("Unimplemented");
    }

    public String toString()
    {
        return "DomHit[" + domId + "@" + time + "]";
    }
}

class HitSorter
    implements Comparator
{
    /**
     * Basic constructor.
     */
    HitSorter()
    {
    }

    /**
     * Compare two hits by source ID and timestamp.
     */
    public int compare(Object o1, Object o2)
    {
        if (o1 == null) {
            if (o2 == null) {
                return 0;
            }

            return 1;
        } else if (o2 == null) {
            return -1;
        } else if (!(o1 instanceof IHitPayload)) {
            if (!(o2 instanceof IHitPayload)) {
                final String name1 = o1.getClass().getName();
                return name1.compareTo(o2.getClass().getName());
            }

            return 1;
        } else if (!(o2 instanceof IHitPayload)) {
            return -1;
        } else {
            return compare((IHitPayload) o1, (IHitPayload) o2);
        }
    }

    /**
     * Compare two hit payloads.
     *
     * @param h1 first hit
     * @param h2 second hit
     *
     * @return standard comparison results
     */
    private int compare(IHitPayload h1, IHitPayload h2)
    {
        int cmp = compare(h1.getHitTimeUTC(), h2.getHitTimeUTC());
        if (cmp == 0) {
            cmp = compare(h1.getSourceID(), h2.getSourceID());
            if (cmp == 0) {
                cmp = compare(h1.getDOMID(), h2.getDOMID());
            }
        }

        return cmp;
    }

    /**
     * Compare two DOM IDs, handling nulls appropriately.
     *
     * @param h1 first DOM ID
     * @param h2 second DOM ID
     *
     * @return standard comparison results
     */
    private int compare(IDOMID s1, IDOMID s2)
    {
        if (s1 == null) {
            if (s2 == null) {
                return 0;
            }

            return 1;
        } else if (s2 == null) {
            return -1;
        } else {
            return (int) (s1.getDomIDAsLong() - s2.getDomIDAsLong());
        }
    }

    /**
     * Compare two source IDs, handling nulls appropriately.
     *
     * @param h1 first ID
     * @param h2 second ID
     *
     * @return standard comparison results
     */
    private int compare(ISourceID s1, ISourceID s2)
    {
        if (s1 == null) {
            if (s2 == null) {
                return 0;
            }

            return 1;
        } else if (s2 == null) {
            return -1;
        } else {
            return s1.getSourceID() - s2.getSourceID();
        }
    }

    /**
     * Compare two UTC times, handling nulls appropriately.
     *
     * @param h1 first time
     * @param h2 second time
     *
     * @return standard comparison results
     */
    private int compare(IUTCTime t1, IUTCTime t2)
    {
        if (t1 == null) {
            if (t2 == null) {
                return 0;
            }

            return 1;
        } else if (t2 == null) {
            return -1;
        } else {
            return (int) (t1.getUTCTimeAsLong() - t2.getUTCTimeAsLong());
        }
    }

    /**
     * Is the specified object a member of the same class?
     *
     * @return <tt>true</tt> if specified object matches this class
     */
    public boolean equals(Object obj)
    {
        if (obj == null) {
            return false;
        }

        return getClass().equals(obj.getClass());
    }

    /**
     * Get sorter hash code.
     *
     * @return hash code for this class.
     */
    public int hashCode()
    {
        return getClass().hashCode();
    }
}

/**
 * Consume DOM hits from stringHub and readout requests
 * from global trigger and send readout data payloads to event builder.
 */
public class Sender
    extends RequestFiller
    implements BufferConsumer, SenderMonitor
{
    /** Hack around lack of official string hub source ID. */
    public static final int STRING_HUB_SOURCE_ID = 12000;
    /** Hack around lack of official string hub name. */
    public static final String DAQ_STRING_HUB = "stringHub";

    private static Log log = LogFactory.getLog(Sender.class);

    /** <tt>true</tt> if we should use the tiny hit payload */
    private static final boolean USE_TINY_HITS = true;

    /** Used to sort hits before building readout data payloads. */
    private static final HitSorter HIT_SORTER = new HitSorter();

    private static int nextPayloadNum;

    private ISourceID sourceId;
    private EngineeringFormatHitDataPayloadFactory engHitFactory;
    private HitPayloadFactory hitFactory;
    private ReadoutRequestPayloadFactory readoutReqFactory;
    private ReadoutDataPayloadFactory readoutDataFactory;

    private IPayloadDestinationCollection hitDest;
    private RequestInputEngine reqInputEngine;
    private IPayloadDestinationCollection dataDest;

    /** list of payloads to be deleted after back end has stopped */
    private ArrayList finalData;

    /** per-run counter for monitoring number of recycled payloads */
    private long numRecycled;

    /** lifetime counter for monitoring number of stops sent */
    private long totStopsSent;

    /** start time for current request */
    private long reqStartTime = Long.MAX_VALUE;
    /** end time for current request */
    private long reqEndTime = Long.MIN_VALUE;

    /** time of most recently queued hit */
    private long latestHitTime;
    /** start time of most recent readout data */
    private long latestReadoutStartTime;
    /** end time of most recent readout data */
    private long latestReadoutEndTime;

    /**
     * Create a readout request filler.
     *
     * @param mgr component manager
     * @param masterFactory master payload factory
     */
    public Sender(int stringHubId, MasterPayloadFactory masterFactory)
    {
        super("Sender", false);

        sourceId = getSourceId(stringHubId);

        engHitFactory = new EngineeringFormatHitDataPayloadFactory();
        hitFactory = new HitPayloadFactory();

        final int readoutReqType = PayloadRegistry.PAYLOAD_ID_READOUT_REQUEST;
        readoutReqFactory = (ReadoutRequestPayloadFactory)
            masterFactory.getPayloadFactory(readoutReqType);

        final int readoutDataType = PayloadRegistry.PAYLOAD_ID_READOUT_DATA;
        readoutDataFactory = (ReadoutDataPayloadFactory)
            masterFactory.getPayloadFactory(readoutDataType);
    }

    /**
     * Quick check to see if specified data is included in
     * the specified request.
     *
     * @param reqPayload request
     * @param dataPayload data being checked
     *
     * @return <tt>-1</tt> if data is earlier than the request,
     *         <tt>0</tt> if data is inside the request, or
     *         <tt>1</tt> if data is later than the request
     */
    public int compareRequestAndData(IPayload reqPayload, IPayload dataPayload)
    {
        IReadoutRequest req = (IReadoutRequest) reqPayload;
        IDomHit data = (IDomHit) dataPayload;

        // get time from current hit
        final long hitTime;
        if (data == null) {
            hitTime = Long.MAX_VALUE;
        } else {
            hitTime = data.getTimestamp();
        }

        if (hitTime < reqStartTime) {
            return -1;
        } else if (hitTime <= reqEndTime) {
            return 0;
        } else {
            return 1;
        }
    }

    /**
     * Add a DOM hit to the queue.
     *
     * @param buf buffer containing DOM hit
     */
    public void consume(ByteBuffer buf)
    {
        if (buf.position() == 0 && buf.limit() == 32 &&
            buf.getInt(0) == 32 && buf.getInt(8) == 0 &&
            buf.getLong(24) == Long.MAX_VALUE)
        {
            if (hitDest != null) {
                try {
                    hitDest.stopAllPayloadDestinations();
                } catch (IOException ioe) {
                    if (log.isErrorEnabled()) {
                        log.error("Couldn't stop hit destinations", ioe);
                    }
                }
            }

            addDataStop();
        } else {
            ByteBuffer dupBuf = buf.duplicate();

            DomHitEngineeringFormatPayload engData =
                new DomHitEngineeringFormatPayload();
            try {
                engData.initialize(0, dupBuf);
                engData.loadPayload();
            } catch (DataFormatException dfe) {
                log.error("Could not load engineering data", dfe);
                engData = null;
            } catch (IOException ioe) {
                log.error("Could not create hit payload", ioe);
                engData = null;
            }

            if (engData != null) {
                ILoadablePayload payload =
                    hitFactory.createPayload(sourceId,
                                             engData.getTriggerMode(), 0,
                                             engData);

                if (payload == null) {
                    log.error("Couldn't build hit from engineering data");
                } else {
                    if (hitDest != null) {
                        try {
                            hitDest.writePayload((Payload) payload);
                        } catch (IOException ioe) {
                            if (log.isErrorEnabled()) {
                                log.error("Could not send HitPayload", ioe);
                            }
                        }
                    }

                    payload.recycle();
                }

                // remember most recent time for monitoring
                latestHitTime =
                    engData.getPayloadTimeUTC().getUTCTimeAsLong();

                if (!USE_TINY_HITS) {
                    addData(engData);
                } else {
                    engData.recycle();

                    try {
                        addData(new TinyHitPayload(dupBuf));
                    } catch (DataFormatException dfe) {
                        log.error("Couldn't add hit", dfe);
                    } catch (IOException ioe) {
                        log.error("Couldn't add hit", ioe);
                    }
                }
            }
        }

        buf.flip();
    }

    private List convertDataToHits(IReadoutRequest req, List dataList)
        throws DataFormatException, IOException
    {
        ArrayList hitDataList = new ArrayList();

        for (Iterator iter = dataList.iterator(); iter.hasNext(); ) {
            IDomHit domHit = (IDomHit) iter.next();

            final int triggerMode = 2;

            DomHitEngineeringFormatPayload engData;
            if (!USE_TINY_HITS) {
                // XXX I'd LOVE to avoid the deepCopy here, but the parent
                // RequestFiller class is holding a pointer to 'engData'
                // and will free it after the ReadoutDataPayload is sent,
                // so we need to make a copy here in order to avoid
                // the 'engData' payload being recycled twice.
                Object obj =
                    ((DomHitEngineeringFormatPayload) domHit).deepCopy();
                engData = (DomHitEngineeringFormatPayload) obj;
            } else {
                engData = new DomHitEngineeringFormatPayload();
                try {
                    engData.initialize(0, domHit.getPayloadBacking());
                    engData.loadPayload();
                } catch (DataFormatException dfe) {
                    log.error("Could not load engineering data", dfe);
                    engData = null;
                } catch (IOException ioe) {
                    log.error("Could not create hit payload", ioe);
                    engData = null;
                }
            }

            hitDataList.add(engHitFactory.createPayload(sourceId, -1,
                                                        triggerMode,
                                                        engData));
        }

        return hitDataList;
    }

    /**
     * Recycle a single payload.
     *
     * @param data payload to recycle
     */
    public void disposeData(ILoadablePayload data)
    {
        data.recycle();
    }

    /**
     * Recycle the list of payloads.
     *
     * @param dataList list of payloads to recycle
     */
    public void disposeDataList(List dataList)
    {
        Iterator iter = dataList.iterator();
        while (iter.hasNext()) {
            Object obj = iter.next();
            if (obj instanceof ILoadablePayload) {
                disposeData(((ILoadablePayload) obj));
            } else {
                log.error("Not disposing of non-loadable " + obj + " (" +
                          obj.getClass().getName() + ")");
            }
        }
    }

    /**
     * Clean up before worker thread stops running.
     */
    public void finishThreadCleanup()
    {
	log.info("in finishThreadCleanup().");
        if (dataDest != null) {
            try {
                dataDest.stopAllPayloadDestinations();
            } catch (IOException ioe) {
                if (log.isErrorEnabled()) {
                    log.error("Couldn't stop readout data destinations", ioe);
                }
            }
            totStopsSent++;
        }
    }

    /**
     * Get average number of hits per readout.
     *
     * @return hits/readout
     */
    public long getAverageHitsPerReadout()
    {
        return getAverageOutputDataPayloads();
    }

    //public String getBackEndTiming()

    /**
     * Get current rate of hits per second.
     *
     * @return hits/second
     */
    public double getHitsPerSecond()
    {
        return getDataPayloadsPerSecond();
    }

    /**
     * Get the time of the most recently queued hit.
     *
     * @return latest time
     */
    public long getLatestHitTime()
    {
        return latestHitTime;
    }

    /**
     * Get the end time of the most recent readout data payload.
     *
     * @return latest readout data end time
     */
    public long[] getLatestReadoutTimes()
    {
        return new long[] { latestReadoutStartTime, latestReadoutEndTime };
    }

    /**
     * Get number of hits which could not be loaded.
     *
     * @return number of bad hits received
     */
    public long getNumBadHits()
    {
        return getNumBadDataPayloads();
    }

    /**
     * Number of readout requests which could not be loaded.
     *
     * @return number of bad readout requests
     */
    public long getNumBadReadoutRequests()
    {
        return getNumBadRequests();
    }

    /**
     * Get number of hits cached for readout being built
     *
     * @return number of cached hits
     */
    public int getNumHitsCached()
    {
        return getNumDataPayloadsCached();
    }

    /**
     * Get number of hits thrown away.
     *
     * @return number of hits thrown away
     */
    public long getNumHitsDiscarded()
    {
        return getNumDataPayloadsDiscarded();
    }

    /**
     * Get number of hits queued for processing.
     *
     * @return number of hits queued
     */
    public int getNumHitsQueued()
    {
        return getNumDataPayloadsQueued();
    }

    /**
     * Get number of hits received.
     *
     * @return number of hits received
     */
    public long getNumHitsReceived()
    {
        return getNumDataPayloadsReceived();
    }

    /**
     * Get number of null hits received.
     *
     * @return number of null hits received
     */
    public long getNumNullHits()
    {
        return getNumNullDataPayloads();
    }

    /**
     * Get number of readouts which could not be created.
     *
     * @return number of null readouts
     */
    public long getNumNullReadouts()
    {
        return getNumNullOutputs();
    }

    /**
     * Number of readout requests dropped while stopping.
     *
     * @return number of readout requests dropped
     */
    public long getNumReadoutRequestsDropped()
    {
        return getNumRequestsDropped();
    }

    /**
     * Number of readout requests queued for processing.
     *
     * @return number of readout requests queued
     */
    public long getNumReadoutRequestsQueued()
    {
        return getNumRequestsQueued();
    }

    /**
     * Number of readout requests received for this run.
     *
     * @return number of readout requests received
     */
    public long getNumReadoutRequestsReceived()
    {
        return getNumRequestsReceived();
    }

    /**
     * Get number of readouts which could not be sent.
     *
     * @return number of failed readouts
     */
    public long getNumReadoutsFailed()
    {
        return getNumOutputsFailed();
    }

    /**
     * Get number of empty readouts which were ignored.
     *
     * @return number of ignored readouts
     */
    public long getNumReadoutsIgnored()
    {
        return getNumOutputsIgnored();
    }

    /**
     * Get number of readouts sent.
     *
     * @return number of readouts sent
     */
    public long getNumReadoutsSent()
    {
        return getNumOutputsSent();
    }

    /**
     * Get number of recycled payloads.
     *
     * @return number of recycled payloads
     */
    public long getNumRecycled()
    {
        return numRecycled;
    }

    /**
     * Get number of hits not used for a readout.
     *
     * @return number of unused hits
     */
    public long getNumUnusedHits()
    {
        return getNumUnusedDataPayloads();
    }

    /**
     * Get current rate of readout requests per second.
     *
     * @return readout requests/second
     */
    public double getReadoutRequestsPerSecond()
    {
        return getRequestsPerSecond();
    }

    /**
     * Get current rate of readouts per second.
     *
     * @return readouts/second
     */
    public double getReadoutsPerSecond()
    {
        return getOutputsPerSecond();
    }

    private static ISourceID getSourceId(int compId)
    {
        final String compName = DAQ_STRING_HUB;

        return SourceIdRegistry.getISourceIDFromNameAndId(compName, compId);
    }

    /**
     * Get total number of hits which could not be loaded since last reset.
     *
     * @return total number of bad hits since last reset
     */
    public long getTotalBadHits()
    {
        return getTotalBadDataPayloads();
    }

    //public long getTotalDataStopsReceived()

    /**
     * Total number of hits thrown away since last reset.
     *
     * @return total number of hits thrown away since last reset
     */
    public long getTotalHitsDiscarded()
    {
        return getTotalDataPayloadsDiscarded();
    }

    /**
     * Total number of hits received since last reset.
     *
     * @return total number of hits received since last reset
     */
    public long getTotalHitsReceived()
    {
        return getTotalDataPayloadsReceived();
    }

    /**
     * Total number of readout requests received since the last reset.
     *
     * @return total number of readout requests received since the last reset
     */
    public long getTotalReadoutRequestsReceived()
    {
        return getTotalRequestsReceived();
    }

    /**
     * Total number of readouts since last reset which could not be sent.
     *
     * @return total number of failed readouts
     */
    public long getTotalReadoutsFailed()
    {
        return getTotalOutputsFailed();
    }

    /**
     * Total number of empty readouts which were ignored since the last reset.
     *
     * @return total number of ignored readouts since last reset
     */
    public long getTotalReadoutsIgnored()
    {
        return getTotalOutputsIgnored();
    }

    /**
     * Total number of readouts sent since last reset.
     *
     * @return total number of readouts sent since last reset.
     */
    public long getTotalReadoutsSent()
    {
        return getTotalOutputsSent();
    }

    //public long getTotalRequestStopsReceived()

    public long getTotalStopsSent()
    {
        return totStopsSent;
    }

    /**
     * Is the specified data payload included in the specified request?
     *
     * @param reqPayload request
     * @param dataPayload data
     *
     * @return <tt>true</tt> if the data is included in the request.
     */
    public boolean isRequested(IPayload reqPayload, IPayload dataPayload)
    {
        IDomHit curData = (IDomHit) dataPayload;
        IReadoutRequest curReq = (IReadoutRequest) reqPayload;

        // get time from current hit
        final long timestamp = curData.getTimestamp();

        final int srcId = sourceId.getSourceID();

        Iterator iter =
            curReq.getReadoutRequestElements().iterator();
        while (iter.hasNext()) {
            IReadoutRequestElement elem =
                (IReadoutRequestElement) iter.next();

            final long elemFirstUTC =
                elem.getFirstTimeUTC().getUTCTimeAsLong();
            final long elemLastUTC =
                elem.getLastTimeUTC().getUTCTimeAsLong();

            if (timestamp < elemFirstUTC || timestamp > elemLastUTC) {
                continue;
            }

            final String daqName;
            if (elem.getSourceID().getSourceID() < 0) {
                daqName = null;
            } else {
                daqName =
                    SourceIdRegistry.getDAQNameFromISourceID(elem.getSourceID());
            }

            switch (elem.getReadoutType()) {
            case IReadoutRequestElement.READOUT_TYPE_GLOBAL:
                return true;
            case IReadoutRequestElement.READOUT_TYPE_II_GLOBAL:
                if (daqName.equals(DAQCmdInterface.DAQ_STRINGPROCESSOR) ||
                    daqName.equals(DAQCmdInterface.DAQ_PAYLOAD_INVALID_SOURCE_ID))
                {
                    return true;
                }
                break;
            case IReadoutRequestElement.READOUT_TYPE_IT_GLOBAL:
                if (daqName.equals(DAQCmdInterface.DAQ_ICETOP_DATA_HANDLER) ||
                    daqName.equals(DAQCmdInterface.DAQ_PAYLOAD_INVALID_SOURCE_ID))
                {
                    return true;
                }
                break;
            case IReadoutRequestElement.READOUT_TYPE_II_STRING:
                if (srcId == elem.getSourceID().getSourceID()) {
                    return true;
                }
                break;
            case IReadoutRequestElement.READOUT_TYPE_II_MODULE:
                if (daqName.equals(DAQCmdInterface.DAQ_STRINGPROCESSOR) &&
                    curData.getDomId() == elem.getDomID().getDomIDAsLong())
                {
                    return true;
                }
                break;
            case IReadoutRequestElement.READOUT_TYPE_IT_MODULE:
                if (daqName.equals(DAQCmdInterface.DAQ_ICETOP_DATA_HANDLER) &&
                    curData.getDomId() == elem.getDomID().getDomIDAsLong())
                {
                    return true;
                }
                break;
            default:
                if (log.isErrorEnabled()) {
                    log.error("Unknown request type #" +
                              elem.getReadoutType());
                }
                break;
            }
        }

        return false;
    }

    /**
     * Build a readout data payload using the specified request and hits.
     *
     * @param reqPayload request payload
     * @param dataList list of hits to include
     *
     * @return readout data payload
     */
    public ILoadablePayload makeDataPayload(IPayload reqPayload,
                                            List dataList)
    {
        if (reqPayload == null) {
            try {
                throw new NullPointerException("No request");
            } catch (Exception ex) {
                log.error("No current request; cannot send data", ex);
            }

            return null;
        }

        IReadoutRequest req = (IReadoutRequest) reqPayload;

        IUTCTime startTime = null;
        IUTCTime endTime = null;

        Iterator iter = req.getReadoutRequestElements().iterator();
        while (iter.hasNext()) {
            IReadoutRequestElement elem =
                (IReadoutRequestElement) iter.next();

            if (startTime == null) {
                startTime = elem.getFirstTimeUTC();
            } else {
                long tmpTime = elem.getFirstTimeUTC().getUTCTimeAsLong();
                if (tmpTime < startTime.getUTCTimeAsLong()) {
                    startTime = elem.getFirstTimeUTC();
                }
            }

            if (endTime == null) {
                endTime = elem.getLastTimeUTC();
            } else {
                long tmpTime = elem.getLastTimeUTC().getUTCTimeAsLong();
                if (tmpTime > endTime.getUTCTimeAsLong()) {
                    endTime = elem.getLastTimeUTC();
                }
            }
        }

        if (startTime == null || endTime == null) {
            log.error("Request may have been recycled; cannot send data");
            return null;
        }

        latestReadoutStartTime = startTime.getUTCTimeAsLong();
        latestReadoutEndTime = endTime.getUTCTimeAsLong();

        if (log.isDebugEnabled()) {
            log.debug("Closing ReadoutData " + startTime.getUTCTimeAsLong() +
                      " - " + endTime.getUTCTimeAsLong());
        }
        if (log.isWarnEnabled() && dataList.size() == 0) {
            log.warn("Sending empty readout data payload for window [" +
                     startTime.getUTCTimeAsLong() + " - " +
                     endTime.getUTCTimeAsLong() + "]");
        }

        List hitDataList;
        try {
            hitDataList = convertDataToHits(req, dataList);
        } catch (DataFormatException dfe) {
            log.error("Couldn't convert engineering data to hits", dfe);
            return null;
        } catch (IOException ioe) {
            log.error("Couldn't convert engineering data to hits", ioe);
            return null;
        }

        // sort by timestamp/source ID
        Collections.sort(hitDataList, HIT_SORTER);

        Vector tmpHits = new Vector(hitDataList);

        final int uid = req.getUID();
        // payload number is deprecated; set it to bogus value
        final int payloadNum = nextPayloadNum++;

        // build readout data
        Payload readout =
            readoutDataFactory.createPayload(uid, payloadNum, true, sourceId,
                                             startTime, endTime, tmpHits);

        Iterator hitIter = hitDataList.iterator();
        while (hitIter.hasNext()) {
            Payload pay = (Payload) hitIter.next();
            pay.recycle();
        }

        return readout;
    }

    /**
     * Recycle all payloads in the list.
     *
     * @param payloadList list of payloads
     */
    public void recycleAll(Collection payloadList)
    {
        Iterator iter = payloadList.iterator();
        while (iter.hasNext()) {
            Payload payload = (Payload) iter.next();
            payload.recycle();
            numRecycled++;
        }
    }

    /**
     * Recycled any left-over data before exiting.
     */
    public void recycleFinalData()
    {
        if (finalData != null) {
            recycleAll(finalData);

            // delete list once everything's been recycled
            finalData = null;
        }
    }

    /**
     * Send the payload to the output engine.
     *
     * @param payload readout data to send
     *
     * @return <tt>true</tt> if the payload was sent
     */
    public boolean sendOutput(ILoadablePayload payload)
    {
        boolean sent = false;
        if (dataDest == null) {
            if (log.isErrorEnabled()) {
                log.error("ReadoutDataPayload destination has not been set");
            }
        } else {
            try {
                dataDest.writePayload((Payload) payload);
                sent = true;
            } catch (IOException ioe) {
                if (log.isErrorEnabled()) {
                    log.error("Could not send RequestDataPayload", ioe);
                }
            }
        }

        payload.recycle();

        return sent;
    }

    /**
     * Set the output engine where readout data payloads are sent.
     *
     * @param dest output destination
     */
    public void setDataOutputDestination(IPayloadDestinationCollection dest)
    {
        dataDest = dest;
    }

    /**
     * Set the output engine where hit payloads are sent.
     *
     * @param dest output destination
     */
    public void setHitOutputDestination(IPayloadDestinationCollection dest)
    {
        hitDest = dest;
    }

    /**
     * Set the starting and engin times for the current request.
     *
     * @param payload readout request payload
     */
    public void setRequestTimes(IPayload payload)
    {
        IReadoutRequest req = (IReadoutRequest) payload;

        reqStartTime = Long.MAX_VALUE;
        reqEndTime = Long.MIN_VALUE;

        Iterator iter =
            req.getReadoutRequestElements().iterator();
        while (iter.hasNext()) {
            IReadoutRequestElement elem =
                (IReadoutRequestElement) iter.next();

            long startTime =
                elem.getFirstTimeUTC().getUTCTimeAsLong();
            if (startTime < reqStartTime) {
                reqStartTime = startTime;
            }

            long endTime =
                elem.getLastTimeUTC().getUTCTimeAsLong();
            if (endTime > reqEndTime) {
                reqEndTime = endTime;
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Filling readout#" + req.getUID() +
                      " [" + reqStartTime + "-" +
                      reqEndTime + "]");
        }
    }
}


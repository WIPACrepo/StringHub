package icecube.daq.sender;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.eventbuilder.impl.ReadoutDataPayloadFactory;
import icecube.daq.io.DAQOutputChannelManager;
import icecube.daq.io.OutputChannel;
import icecube.daq.monitoring.SenderMonitor;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IDOMID;
import icecube.daq.payload.IDomHit;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadDestination;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.DomHitDeltaCompressedFormatPayload;
import icecube.daq.payload.impl.DomHitEngineeringFormatPayload;
import icecube.daq.payload.splicer.Payload;
import icecube.daq.reqFiller.RequestFiller;
import icecube.daq.trigger.IHitPayload;
import icecube.daq.trigger.IReadoutRequest;
import icecube.daq.trigger.IReadoutRequestElement;
import icecube.daq.trigger.impl.DeltaCompressedFormatHitDataPayloadFactory;
import icecube.daq.trigger.impl.EngineeringFormatHitDataPayloadFactory;
import icecube.daq.trigger.impl.HitPayloadFactory;

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

    public int getLocalCoincidenceMode()
    {
        throw new Error("Unimplemented");
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
        return "DomHit[" + Long.toHexString(domId) + "@" + time + "]";
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
            return (int) (s1.longValue() - s2.longValue());
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
            return (int) (t1.longValue() - t2.longValue());
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

class DomHitFactory
{
    private static Log log = LogFactory.getLog(DomHitFactory.class);

    IDomHit createPayload(int offset, ByteBuffer buf)
        throws DataFormatException, IOException
    {
        if (offset != 0) {
            throw new Error("Offset should always be zero");
        }

        if (buf.limit() < offset + 4) {
            throw new Error("Expected buffer with at least " + (offset + 4) +
                            " bytes, not " + buf.limit() + " (offset=" +
                            offset + ")");
        }

        final int len = buf.getInt(offset + 0);
        if (buf.limit() < offset + len) {
            throw new Error("Payload at offset " + offset + " requires " +
                            len + " bytes, but buffer limit is " + buf.limit());
        }

        final int type = buf.getInt(offset + 4);
        switch (type) {
        case 2:
            DomHitEngineeringFormatPayload engHit =
                new DomHitEngineeringFormatPayload();
            engHit.initialize(offset + 0, buf);
            return engHit;
        case 3:
        case PayloadRegistry.PAYLOAD_ID_DELTA_HIT:
            DomHitDeltaCompressedFormatPayload deltaHit =
                new DomHitDeltaCompressedFormatPayload();
            // XXX rewrite payload type to match real payload type
            buf.putInt(offset + 4, deltaHit.getPayloadType());
            deltaHit.initialize(offset + 0, buf);
            return deltaHit;
        default:
            break;
        }

        log.error("Ignoring unknown hit type " + type + " in " + len +
                  "-byte payload");
        return null;
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
    private static Log log = LogFactory.getLog(Sender.class);

    /** <tt>true</tt> if we should use the tiny hit payload */
    private static final boolean USE_TINY_HITS = true;

    /** Used to sort hits before building readout data payloads. */
    private static final HitSorter HIT_SORTER = new HitSorter();

    protected static final int DEFAULT_TRIGGER_MODE = 2;

    private static int nextPayloadNum;

    private ISourceID sourceId;
    private EngineeringFormatHitDataPayloadFactory engHitFactory;
    private DeltaCompressedFormatHitDataPayloadFactory deltaHitFactory;
    private HitPayloadFactory hitFactory;
    private DomHitFactory domHitFactory;
    private ReadoutDataPayloadFactory readoutDataFactory;

    private DAQOutputChannelManager hitOut;
    private OutputChannel hitChan;
    private IByteBufferCache hitCache;

    private DAQOutputChannelManager dataOut;
    private OutputChannel dataChan;
    private IByteBufferCache dataCache;

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

    /** Set to <tt>true</tt> to forward hits with LCMode==0 to the trigger */
    private boolean forwardLC0Hits;

    /**
     * Create a readout request filler.
     *
     * @param stringHubId this stringHub's ID
     * @param rdoutDataMgr ReadoutDataPayload byte buffer cache
     */
    public Sender(int stringHubId, IByteBufferCache rdoutDataMgr)
    {
        super("Sender#" + stringHubId, false);

        sourceId = getSourceId(stringHubId % 1000);

        engHitFactory = new EngineeringFormatHitDataPayloadFactory();
        deltaHitFactory = new DeltaCompressedFormatHitDataPayloadFactory();
        hitFactory = new HitPayloadFactory();
        domHitFactory = new DomHitFactory();

        readoutDataFactory = new ReadoutDataPayloadFactory();
        readoutDataFactory.setByteBufferCache(rdoutDataMgr);

        dataCache = rdoutDataMgr;

        forwardLC0Hits = false;
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
        if (buf.getInt(0) == 32 && buf.getLong(24) == Long.MAX_VALUE)
        {
            if (hitChan != null) {
                try {
                    hitChan.sendLastAndStop();
                } catch (Exception ex) {
                    if (log.isErrorEnabled()) {
                        log.error("Couldn't stop hit destinations", ex);
                    }
                }
            }

            addDataStop();
        } else {
            ByteBuffer dupBuf = buf.duplicate();

            IDomHit engData;
            try {
                engData = (IDomHit) domHitFactory.createPayload(0, dupBuf);
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
                    log.error("Couldn't build hit from DOM hit data");
                } else if (hitChan != null &&
                           (forwardLC0Hits || 
                                   engData.getLocalCoincidenceMode() != 0 ||
                                   engData.getTriggerMode() == 4))
                {
                    ByteBuffer payBuf;
                    if (hitCache != null) {
                        payBuf =
                            hitCache.acquireBuffer(payload.getPayloadLength());
                    } else {
                        payBuf =
                            ByteBuffer.allocate(payload.getPayloadLength());
                    }

                    try {
                        ((IWriteablePayload) payload).writePayload(false, 0,
                                                                   payBuf);
                    } catch (IOException ioe) {
                        log.error("Couldn't create payload", ioe);
                        payBuf = null;
                    }

                    if (payBuf != null) {
                        hitChan.receiveByteBuffer(payBuf);
                    }

                    payload.recycle();
                }

                // remember most recent time for monitoring
                latestHitTime =
                    engData.getPayloadTimeUTC().longValue();

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

    private List convertDataToHits(List dataList)
        throws DataFormatException, IOException
    {
        ArrayList hitDataList = new ArrayList();

        for (Iterator iter = dataList.iterator(); iter.hasNext(); ) {
            IDomHit domHit = (IDomHit) iter.next();

            IDomHit hitCopy;
            if (!USE_TINY_HITS) {
                // XXX I'd LOVE to avoid the deepCopy here, but the parent
                // RequestFiller class is holding a pointer to 'domHit'
                // and will free it after the ReadoutDataPayload is sent,
                // so we need to make a copy here in order to avoid
                // the 'domHit' payload being recycled twice.
                hitCopy = (IDomHit) domHit.deepCopy();
            } else {
                try {
                    ByteBuffer buf = domHit.getPayloadBacking();
                    hitCopy = (IDomHit) domHitFactory.createPayload(0, buf);
                    hitCopy.loadPayload();
                } catch (DataFormatException dfe) {
                    log.error("Could not load engineering data", dfe);
                    hitCopy = null;
                } catch (IOException ioe) {
                    log.error("Could not create hit payload", ioe);
                    hitCopy = null;
                }
            }

            Object newHit;
            switch (hitCopy.getPayloadType()) {
            case PayloadRegistry.PAYLOAD_ID_DELTA_HIT:
                DomHitDeltaCompressedFormatPayload delta =
                    (DomHitDeltaCompressedFormatPayload) hitCopy;
                newHit = deltaHitFactory.createPayload(sourceId,
                                                       delta.getTriggerMode(),
                                                       delta);
                break;
            case PayloadRegistry.PAYLOAD_ID_ENGFORMAT_HIT:
                DomHitEngineeringFormatPayload engData =
                    (DomHitEngineeringFormatPayload) hitCopy;
                newHit = engHitFactory.createPayload(sourceId, -1,
                                                     engData.getTriggerMode(),
                                                     engData);
                break;
            default:
                log.error("Unknown hit type " + hitCopy.getPayloadType());
                newHit = null;
                break;
            }

            if (newHit != null) {
                hitDataList.add(newHit);
            }
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
        try {
            dataChan.sendLastAndStop();
        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error("Couldn't stop readout data destinations", ex);
            }
        }
        totStopsSent++;
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
     * Get number of hits dropped while stopping.
     *
     * @return number of hits dropped
     */
    public long getNumHitsDropped()
    {
        return getNumDataPayloadsDropped();
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
        final String compName = DAQCmdInterface.DAQ_STRING_HUB;

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

            final long elemFirstUTC = elem.getFirstTimeUTC().longValue();
            final long elemLastUTC = elem.getLastTimeUTC().longValue();

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
                    curData.getDomId() == elem.getDomID().longValue())
                {
                    return true;
                }
                break;
            case IReadoutRequestElement.READOUT_TYPE_IT_MODULE:
                if (daqName.equals(DAQCmdInterface.DAQ_ICETOP_DATA_HANDLER) &&
                    curData.getDomId() == elem.getDomID().longValue())
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
                long tmpTime = elem.getFirstTimeUTC().longValue();
                if (tmpTime < startTime.longValue()) {
                    startTime = elem.getFirstTimeUTC();
                }
            }

            if (endTime == null) {
                endTime = elem.getLastTimeUTC();
            } else {
                long tmpTime = elem.getLastTimeUTC().longValue();
                if (tmpTime > endTime.longValue()) {
                    endTime = elem.getLastTimeUTC();
                }
            }
        }

        if (startTime == null || endTime == null) {
            log.error("Request may have been recycled; cannot send data");
            return null;
        }

        latestReadoutStartTime = startTime.longValue();
        latestReadoutEndTime = endTime.longValue();

        if (log.isDebugEnabled()) {
            log.debug("Closing ReadoutData " + startTime + " - " + endTime);
        }
        if (log.isWarnEnabled() && dataList.size() == 0) {
            log.warn("Sending empty readout data payload for window [" +
                     startTime + " - " + endTime + "]");
        }

        List hitDataList;
        try {
            hitDataList = convertDataToHits(dataList);
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
        IPayload readout =
            readoutDataFactory.createPayload(uid, payloadNum, true, sourceId,
                                             startTime, endTime, tmpHits);

        Iterator hitIter = hitDataList.iterator();
        while (hitIter.hasNext()) {
            Payload pay = (Payload) hitIter.next();
            pay.recycle();
        }

        return (ILoadablePayload) readout;
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
        ByteBuffer buf;
        if (dataCache == null) {
            buf = ByteBuffer.allocate(payload.getPayloadLength());
        } else {
            buf = dataCache.acquireBuffer(payload.getPayloadLength());
        }
        try {
            ((IWriteablePayload) payload).writePayload(false, 0, buf);
        } catch (Exception ex) {
            log.error("Couldn't create payload", ex);
            buf = null;
        }

        if (buf != null) {
            dataChan.receiveByteBuffer(buf);
            sent = true;
        }

        payload.recycle();

        return sent;
    }

    /**
     * Set the output engine where readout data payloads are sent.
     *
     * @param dest output destination
     */
    public void setDataOutput(DAQOutputChannelManager dest)
    {
        dataOut = dest;
        dataChan = null;
    }

    /**
     * Set the buffer cache where hit payloads tracked.
     *
     * @param cache hit buffer cache
     */
    public void setHitCache(IByteBufferCache cache)
    {
        hitCache = cache;
    }

    /**
     * Set the output engine where hit payloads are sent.
     *
     * @param dest output destination
     */
    public void setHitOutput(DAQOutputChannelManager dest)
    {
        hitOut = dest;
        hitChan = null;
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

            long startTime = elem.getFirstTimeUTC().longValue();
            if (startTime < reqStartTime) {
                reqStartTime = startTime;
            }

            long endTime = elem.getLastTimeUTC().longValue();
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

    /**
     * Get the output channels before the request thread is started.
     */
    public void startThread()
    {
        if (hitOut != null) {
            hitChan = hitOut.getChannel();
            if (hitChan == null) {
                throw new Error("Hit destination has no output channels");
            }
        }

        if (dataOut == null) {
            if (log.isErrorEnabled()) {
                throw new Error("Data destination has not been set");
            }
        } else {
            dataChan = dataOut.getChannel();
            if (dataChan == null) {
                throw new Error("Data destination has no output channels");
            }
        }

        super.startThread();
    }

    public void forwardIsolatedHitsToTrigger()
    {
        forwardIsolatedHitsToTrigger(true);
    }

    public void forwardIsolatedHitsToTrigger(boolean forward)
    {
        forwardLC0Hits = forward;
    }

    public String toString()
    {
        if (sourceId == null) {
            return "UNKNOWN";
        }

        return sourceId.toString();
    }
}

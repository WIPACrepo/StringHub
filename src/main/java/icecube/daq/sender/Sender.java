package icecube.daq.sender;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.common.EventVersion;
import icecube.daq.io.DAQOutputChannelManager;
import icecube.daq.io.OutputChannel;
import icecube.daq.monitoring.SenderMonitor;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.IReadoutRequestElement;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.DOMHit;
import icecube.daq.payload.impl.DOMHitFactory;
import icecube.daq.payload.impl.DOMHitReadoutData;
import icecube.daq.payload.impl.DOMID;
import icecube.daq.payload.impl.HitRecordList;
import icecube.daq.reqFiller.RequestFiller;
import icecube.daq.util.DOMRegistry;
import icecube.daq.util.DeployedDOM;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
        } else if (!(o1 instanceof DOMHit)) {
            if (!(o2 instanceof DOMHit)) {
                final String name1 = o1.getClass().getName();
                return name1.compareTo(o2.getClass().getName());
            }

            return 1;
        } else if (!(o2 instanceof DOMHit)) {
            return -1;
        } else {
            return compare((DOMHit) o1, (DOMHit) o2);
        }
    }

    /**
     * Compare two DOM hits.
     *
     * @param h1 first hit
     * @param h2 second hit
     *
     * @return standard comparison results
     */
    private int compare(DOMHit h1, DOMHit h2)
    {
        if (h1.getTimestamp() < h2.getTimestamp()) {
            return -1;
        } else if (h1.getTimestamp() > h2.getTimestamp()) {
            return 1;
        }

        int cmp = h1.getSourceID().getSourceID() - h2.getSourceID().getSourceID();
        if (cmp == 0) {
            if (h1.getDomId() < h2.getDomId()) {
                return -1;
            } else if (h1.getDomId() < h2.getDomId()) {
                return 1;
            }
        }

        return cmp;
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
    protected static final int DEFAULT_TRIGGER_MODE = 2;

    private static Log log = LogFactory.getLog(Sender.class);

    /** Used to sort hits before building readout data payloads. */
    private static final HitSorter HIT_SORTER = new HitSorter();

    private ISourceID sourceId;

    private DAQOutputChannelManager hitOut;
    private OutputChannel hitChan;
    private IByteBufferCache hitCache;

    private DAQOutputChannelManager teOut;
    private OutputChannel teChan;
    private IByteBufferCache teCache;

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

    /** DOM information from default-dom-geometry.xml */
    private DOMRegistry domRegistry;

    // per-run monitoring counter
    private long numTEHits;

    /**
     * 'true' if we've logged an error about missing TE output engine or cache
     */
    private boolean warnedTE;

    /**
     * Create a readout request filler.
     *
     * @param stringHubId this stringHub's ID
     * @param rdoutDataMgr readout data byte buffer cache
     */
    public Sender(int stringHubId, IByteBufferCache rdoutDataMgr)
    {
        super("Sender#" + stringHubId, false);

        sourceId = getSourceId(stringHubId % 1000);

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
        DOMHit data = (DOMHit) dataPayload;

        // get time from current hit
        final long hitTime;
        if (data == null) {
            hitTime = Long.MAX_VALUE;
        } else {
            hitTime = data.getTimestamp();
        }

        int cmpVal;
        if (hitTime < reqStartTime) {
            cmpVal = -1;
        } else if (hitTime <= reqEndTime) {
            cmpVal = 0;
        } else {
            cmpVal = 1;
        }

        return cmpVal;
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
            // process stop message
            if (hitChan != null) {
                try {
                    hitChan.sendLastAndStop();
                } catch (Exception ex) {
                    if (log.isErrorEnabled()) {
                        log.error("Couldn't stop hit destinations", ex);
                    }
                }
            }

            if (teChan != null) {
                try {
                    teChan.sendLastAndStop();
                } catch (Exception ex) {
                    if (log.isErrorEnabled()) {
                        log.error("Couldn't stop track engine destinations",
                                  ex);
                    }
                }
            }

            try {
                addDataStop();
            } catch (IOException ioe) {
                if (log.isErrorEnabled()) {
                    log.error("Couldn't add data stop to queue", ioe);
                }
            }
        } else {
            // process hit
            DOMHit tinyHit;
            try {
                tinyHit = DOMHitFactory.getHit(sourceId, buf, 0);
            } catch (PayloadException pe) {
                log.error("Couldn't get hit from buffer", pe);
                tinyHit = null;
            }

            if (tinyHit != null) {

                // remember most recent time for monitoring
                latestHitTime = tinyHit.getTimestamp();

                // save hit so it can be sent to event builder
                try {
                    addData(tinyHit);
                } catch (IOException ioe) {
                    if (log.isErrorEnabled()) {
                        log.error("Couldn't add data to queue", ioe);
                    }
                }

                if (teChan != null) {
                    if (domRegistry == null) {
                        throw new Error("DOM registry has not been set");
                    }

                    DeployedDOM domData =
                        domRegistry.getDom(DOMID.toString(tinyHit.getDomId()));

                    writeTrackEngineHit(tinyHit, domData);
                }

                // send some hits to local trigger component
                if (hitChan != null &&
                    (forwardLC0Hits ||
                     tinyHit.getLocalCoincidenceMode() != 0 ||
                     tinyHit.getTriggerMode() == 4))
                {
                    // extract hit's ByteBuffer
                    ByteBuffer payBuf;
                    try {
                        payBuf = tinyHit.getHitBuffer(hitCache);
                    } catch (PayloadException pe) {
                        log.error("Couldn't get buffer for hit " + tinyHit, pe);
                        payBuf = null;
                    }

                    // transmit ByteBuffer to local trigger component
                    if (payBuf != null) {
                        hitChan.receiveByteBuffer(payBuf);
                    }
                }
            }
        }

        buf.flip();
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
        if (dataChan != null) {
            try {
                dataChan.sendLastAndStop();
            } catch (Exception ex) {
                if (log.isErrorEnabled()) {
                    log.error("Couldn't stop readout data destinations", ex);
                }
            }
        }
        totStopsSent++;
    }

    public void forwardIsolatedHitsToTrigger()
    {
        forwardIsolatedHitsToTrigger(true);
    }

    public void forwardIsolatedHitsToTrigger(boolean forward)
    {
        forwardLC0Hits = forward;
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
     * Get number of hits sent to the track engine.
     *
     * @return number of track engine hits
     */
    public long getNumTEHitsSent()
    {
        return numTEHits;
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
        DOMHit curData = (DOMHit) dataPayload;
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

        final int uid = req.getUID();

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

        if (log.isWarnEnabled() && dataList.size() == 0) {
            log.warn("Sending empty hit record list " + uid + " window [" +
                     startTime + " - " + endTime + "]");
        } else if (log.isDebugEnabled()) {
            log.debug("Closing hit record list " + uid + " window [" +
                      startTime + " - " + endTime + "]");
        }

        List<DOMHit> hitDataList = new ArrayList<DOMHit>();
        for (Object obj : dataList) {
            hitDataList.add((DOMHit) obj);
        }

        // sort by timestamp/source ID
        Collections.sort(hitDataList, HIT_SORTER);

        if (domRegistry == null) {
            throw new Error("DOM registry has not been set");
        }

        // build readout data
        ILoadablePayload readout;
        if (EventVersion.VERSION < 5) {
            readout = new DOMHitReadoutData(uid, sourceId, startTime, endTime,
                                            hitDataList);
        } else {
            try {
                readout =
                    new HitRecordList(domRegistry, startTime.longValue(), uid,
                                      sourceId, hitDataList);
            } catch (PayloadException pe) {
                log.error("Cannot build list of hit records", pe);
                return null;
            }
        }
/*
        Iterator hitIter = hitDataList.iterator();
        while (hitIter.hasNext()) {
            ILoadablePayload pay = (ILoadablePayload) hitIter.next();
            pay.recycle();
        }
*/

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
            ILoadablePayload payload = (ILoadablePayload) iter.next();
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
     * Reset the back end after it has been stopped.
     */
    public void reset()
    {
        numTEHits = 0;

        super.reset();
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

        if (buf != null && dataChan != null) {
            dataChan.receiveByteBuffer(buf);
            sent = true;
        }

        payload.recycle();

        return sent;
    }

    /**
     * Set the DOM registry.
     *
     * @param reg DOM registry
     */
    public void setDOMRegistry(DOMRegistry reg)
    {
        domRegistry = reg;
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
     * Set the buffer cache where hit payloads are tracked.
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
     * Set the buffer cache where track engine hit payloads are tracked.
     *
     * @param cache track engine buffer cache
     */
    public void setTrackEngineCache(IByteBufferCache cache)
    {
        teCache = cache;
    }

    /**
     * Set the output engine where track engine hits payloads are sent.
     *
     * @param dest output destination
     */
    public void setTrackEngineOutput(DAQOutputChannelManager dest)
    {
        teOut = dest;
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

        if (teOut != null) {
            teChan = teOut.getChannel();
            // teChan can be null if track engine is not part of the run
        }

        if (dataOut == null) {
            if (log.isErrorEnabled()) {
                log.error("Data destination has not been set");
            }
        } else {
            dataChan = dataOut.getChannel();
            if (dataChan == null) {
                throw new Error("Data destination has no output channels");
            }
        }

        super.startThread();
    }

    /**
     * Send subset of hit data to the track engine.
     *
     * @param tinyHit original hit data
     * @param domData data for the DOM which observed this hit
     */
    private void writeTrackEngineHit(DOMHit tinyHit, DeployedDOM domData)
    {
        if (teCache == null) {
            if (!warnedTE) {
                log.error("Cannot write hit to Track Engine:" +
                          " missing buffer cache");
                warnedTE = true;
            }
        } else if (teChan == null) {
            if (!warnedTE) {
                log.error("Cannot write hit to Track Engine:" +
                          " missing output channel");
                warnedTE = true;
            }
        } else {
            ByteBuffer teHit = teCache.acquireBuffer(11);
            teHit.clear();

            teHit.put((byte) (domData.getStringMajor() % Byte.MAX_VALUE));
            teHit.put((byte) (domData.getStringMinor() % Byte.MAX_VALUE));
            teHit.putLong(tinyHit.getTimestamp());
            teHit.put((byte) (tinyHit.getLocalCoincidenceMode() % 0xff));

            teHit.flip();

            teChan.receiveByteBuffer(teHit);

            numTEHits++;
        }
    }

    public String toString()
    {
        if (sourceId == null) {
            return "UNKNOWN";
        }

        return sourceId.toString();
    }
}

package icecube.daq.sender;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.io.DAQOutputChannelManager;
import icecube.daq.io.OutputChannel;
import icecube.daq.monitoring.BatchHLCReporter;
import icecube.daq.monitoring.IRunMonitor;
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
import icecube.daq.reqFiller.RequestFiller;
import icecube.daq.util.IDOMRegistry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Consume DOM hits from stringHub and readout requests
 * from global trigger and send readout data payloads to event builder.
 */
public class Sender
    extends RequestFiller
    implements BufferConsumer, SenderMonitor
{

    private static final long DAY_TICKS = 10000000000L * 60L * 60L * 24L;

    private static Log log = LogFactory.getLog(Sender.class);

    private ISourceID sourceId;

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

    /** DOM information from default-dom-geometry.xml */
    private IDOMRegistry domRegistry;

    // previous hit time (used to detect hits with impossibly huge times)
    private long prevHitTime;

    /**
     * HLS hit reporting is propagated to an independent monitor, the
     * reporting is batched to minimize inter-thread transfer overhead.
     *
     * Hubs vary from 200-1200 HLC hits per second so batch up to 150
     * and the reporting latency to 1 second.
     */
    long ONE_SECOND = 10000000000L;
    BatchHLCReporter hlcReporter =
            new BatchHLCReporter.TimedAutoFlush(150, ONE_SECOND);

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

            try {
                addDataStop();
            } catch (IOException ioe) {
                if (log.isErrorEnabled()) {
                    log.error("Couldn't add data stop to queue", ioe);
                }
            }

            // ensure accumulated hlc hits are reported
            hlcReporter.flush();

        } else {
            // process hit
            DOMHit tinyHit;
            try {
                tinyHit = DOMHitFactory.getHit(sourceId, buf, 0);
            } catch (PayloadException pe) {
                log.error("Couldn't get hit from buffer", pe);
                tinyHit = null;
            }

            // validate hit time
            if (tinyHit != null) {
                if (tinyHit.getTimestamp() < 0) {
                    log.error("Negative time for hit " + tinyHit);
                    tinyHit = null;
                } else if (prevHitTime > 0 &&
                           tinyHit.getTimestamp() > prevHitTime + DAY_TICKS)
                {
                    log.error("Bad hit " + tinyHit +
                              "; previous hit time was " + prevHitTime);
                    tinyHit = null;
                } else {
                    prevHitTime = tinyHit.getTimestamp();

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


                    // report HLC hits to external monitor
                    final boolean isHLC = tinyHit.getLocalCoincidenceMode() != 0;
                    if(isHLC)
                    {
                        hlcReporter.reportHLCHit(tinyHit.getDomId(),
                                tinyHit.getTimestamp());
                    }

                    // send some hits to local trigger component
                    if (hitChan != null &&
                            (forwardLC0Hits || isHLC ||
                                    tinyHit.getTriggerMode() == 4))
                    {
                        // extract hit's ByteBuffer
                        ByteBuffer payBuf;
                        try {
                            payBuf = tinyHit.getHitBuffer(hitCache,
                                                          domRegistry);
                        } catch (PayloadException pe) {
                            log.error("Couldn't get buffer for hit " +
                                      tinyHit, pe);
                            payBuf = null;
                        }

                        // transmit ByteBuffer to local trigger component
                        if (payBuf != null) {
                            hitChan.receiveByteBuffer(payBuf);
                        }
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
     * There will be no more data.
     */
    public void endOfStream(long mbid)
    {
        consume(MultiChannelMergeSort.eos(mbid));
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

        return SenderMethods.isRequested(sourceId, curReq, curData);
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
            log.error("No current request; cannot send data",
                    new NullPointerException("No request"));
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

        if (domRegistry == null) {
            throw new Error("DOM registry has not been set");
        }

        try {
            IWriteablePayload readout =
                    SenderMethods.makeDataPayload(uid, startTime.longValue(),
                                                  endTime.longValue(), dataList,
                                                  sourceId, domRegistry);
            return (ILoadablePayload) readout;
        } catch (PayloadException pe) {
            log.error("Cannot build list of hit records", pe);
            return null;
        }


/*
        Iterator hitIter = hitDataList.iterator();
        while (hitIter.hasNext()) {
            ILoadablePayload pay = (ILoadablePayload) hitIter.next();
            pay.recycle();
        }
*/

    }

    /**
     * Recycle all payloads in the list.
     *
     * @param payloadList list of payloads
     */
    private void recycleAll(Collection payloadList)
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
        prevHitTime = 0;

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
            buf = ByteBuffer.allocate(payload.length());
        } else {
            buf = dataCache.acquireBuffer(payload.length());
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
    public void setDOMRegistry(IDOMRegistry reg)
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
     * Set the starting and ending times for the current request.
     *
     * @param payload readout request payload
     */
    public void setRequestTimes(IPayload payload)
    {
        IReadoutRequest req = (IReadoutRequest) payload;
        SenderMethods.TimeRange range = SenderMethods.extractTimeRange(req);

        reqStartTime = range.startUTC;
        reqEndTime = range.endUTC;

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

    public String toString()
    {
        if (sourceId == null) {
            return "UNKNOWN";
        }

        return sourceId.toString();
    }

    public void setRunMonitor(IRunMonitor runMonitor)
    {
        hlcReporter.setRunMonitor(runMonitor);
    }

}

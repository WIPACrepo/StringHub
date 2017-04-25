package icecube.daq.sender;

import icecube.daq.common.DAQCmdInterface;
import icecube.daq.common.EventVersion;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.IReadoutRequestElement;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.DOMHit;
import icecube.daq.payload.impl.DOMHitReadoutData;
import icecube.daq.payload.impl.HitRecordList;
import icecube.daq.payload.impl.UTCTime;
import icecube.daq.util.IDOMRegistry;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Methods for processing readout request objects.
 *
 * These methods were originally extracted from icecube.daq.sender.Sender
 * and modified slightly to reduce dependencies.
 *
 * The intent of this extraction is to prevent duplicate code between
 * the original Sender (icecube.daq.sender.Sender) and the spool backed
 * replacement (icecube.daq.sender.ReadoutRequestFiller).
 */
public class SenderMethods
{

    private static final Logger logger = Logger.getLogger(SenderMethods.class);

    /** Sorts hits by time, sourceID, dom. */
    private static final HitSorter HIT_SORTER = new HitSorter();


    /**
     * Holds a time range extracted from a readout request.
     */
    public static class TimeRange
    {
        public final long startUTC;
        public final long endUTC;

        public TimeRange(final long startUTC, final long endUTC)
        {
            this.startUTC = startUTC;
            this.endUTC = endUTC;
        }
    }

    /**
     * Extract the time range encompassed by the request.
     * @param readoutRequest The request.
     * @return The time range as {startUTC, StopUTC}.
     */
    public static TimeRange extractTimeRange(IReadoutRequest readoutRequest)
    {
        long reqStartTime = Long.MAX_VALUE;
        long reqEndTime = Long.MIN_VALUE;

        Iterator iter =
                readoutRequest.getReadoutRequestElements().iterator();
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

       return new TimeRange(reqStartTime, reqEndTime);
    }

    /**
     * Is the specified data payload included in the specified request?
     *
     * @param sourceId Source of data.
     * @param curReq ReadoutRequest.
     * @param curData Candidate hit data.
     *
     * @return <tt>true</tt> if the data is included in the request.
     */
    public static boolean isRequested(final ISourceID sourceId,
                                      final IReadoutRequest curReq,
                                      final DOMHit curData)
    {

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
                        //todo not possible
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
                        // todo not possible
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
                    logger.error("Unknown request type #" +
                            elem.getReadoutType());
                    break;
            }
        }

        return false;
    }


    /**
     * Build a readout data payload using the specified request details and hits.
     *
     * @param uid The uid of the readout request.
     * @param startUTC The start of the time range from the request.
     * @param endUTC The end of the time range from the request.
     * @param dataList List of hits in the payload.
     * @param sourceId The source of the hits.
     * @param domRegistry The DOM registry.
     *
     * @return Readout data payload
     */
    public static IWriteablePayload makeDataPayload(final int uid,
                                                    final long startUTC,
                                                    final long endUTC,
                                                    final List<DOMHit> dataList,
                                                    final ISourceID sourceId,
                                                    final IDOMRegistry domRegistry)
            throws PayloadException
    {

        // sort by timestamp/source ID
        Collections.sort(dataList, HIT_SORTER);

        // build readout data
        if (EventVersion.VERSION < 5)
        {
            return new DOMHitReadoutData(uid, sourceId,
                                            new UTCTime(startUTC),
                                            new UTCTime(endUTC),
                                            dataList);
        }
        else
        {
            return new HitRecordList(domRegistry, startUTC,
                                          uid,  sourceId, dataList);
        }

    }

    /**
     * Sorts hits by (timestamp, source).
     */
    public static class HitSorter
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
                } else if (h1.getDomId() > h2.getDomId()) {
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


}

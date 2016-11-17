package icecube.daq.domapp.dataprocessor;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.ZeroCrossingRAPCal;
import org.apache.log4j.Logger;

/**
 * Builds the data processor.
 *
 * Several configuration options are provided, mainly for development and
 * testing support. Defaults to an asynchronous processor with an independent
 * processing thread.
 */
public class DataProcessorFactory
{
    static Logger logger = Logger.getLogger(DataProcessorFactory.class);


    /** Processing will use the acquisition thread. */
    private static final boolean USE_CLIENT_THREADED_PROCESSOR =
            Boolean.getBoolean("icecube.daq.domapp.dataprocessor.client-threaded-processing");

    /** Processing will use the acquisition thread and bypass the threaded wrapper. */
    private static final boolean USE_SYNCHRONOUS_PROCESSOR =
            Boolean.getBoolean("icecube.daq.domapp.dataprocessor.synchronous-processing");

    /** Instrument the Processor and print processing performance details */
    private static final boolean PRINT_VERBOSE_PROCESSING_STATS =
            Boolean.getBoolean("icecube.daq.domapp.dataprocessor.verbose-processing-stats");

    /** Insert an error-injecting processor for testing */
    private static final boolean INJECT_PROCESSOR_ERRORS =
            Boolean.getBoolean("icecube.daq.domapp.dataprocessor.inject-testing-errors");


    /**
     * Constructs the processor according to configuration directives.
     */
    public static DataProcessor buildProcessor( final String id,
                                               final DOMConfiguration config,
                                               final long mbid,
                                               final GPSProvider gpsProvider,
                                               final BufferConsumer hitConsumer,
                                               final BufferConsumer supernovaConsumer,
                                               final BufferConsumer moniConsumer,
                                               final BufferConsumer tcalConsumer)
    {

        DataProcessor baseProcessor =
                buildBaseProcessor(config, mbid, gpsProvider,
                hitConsumer, supernovaConsumer, moniConsumer, tcalConsumer);

        if(INJECT_PROCESSOR_ERRORS)
        {
            logger.info("Using error-injecting DataProcessor");
            baseProcessor = new ErrorProducingProcessor(baseProcessor);
        }

        if(USE_SYNCHRONOUS_PROCESSOR)
        {
            logger.info("Using Synchronous DataProcessor");
            return baseProcessor;
        }
        else
        {
            if(USE_CLIENT_THREADED_PROCESSOR)
            {
                logger.info("Using Client-Threaded DataProcessor");
                return AsynchronousDataProcessor.clientThreadExecutor(id,
                        baseProcessor);
            }
            else
            {
                logger.info("Using Threaded DataProcessor");
                return AsynchronousDataProcessor.singleThreadedExecutor(id,
                        baseProcessor);
            }
        }

    }


    /**
     * Builds the base processor which executes synchronously on the callers
     * thread.
     */
    private static DataProcessor buildBaseProcessor(final DOMConfiguration config,
                                              final long mbid,
                                              final GPSProvider gpsProvider,
                                              final BufferConsumer hitConsumer,
                                              final BufferConsumer supernovaConsumer,
                                              final BufferConsumer moniConsumer,
                                              final BufferConsumer tcalConsumer)
    {
        DataStats dataStats = buildDataStats(mbid);

        final RAPCal rapcal = instantiateRAPCal();
        rapcal.setMainboardID(mbid);

        //build the dispatchers
        UTCHitDispatcher hitDispatch =
                new UTCHitDispatcher(hitConsumer,
                        config, rapcal, mbid);
        UTCDispatcher supernovaDispatcher =
                new UTCMonotonicDispatcher(supernovaConsumer,
                        DataProcessor.StreamType.SUPERNOVA,
                        rapcal, mbid);
        UTCDispatcher moniDispatcher =
                new UTCMonotonicDispatcher(moniConsumer,
                        DataProcessor.StreamType.MONI,
                        rapcal, mbid);
        UTCDispatcher tcalDispatcher =
                new UTCMonotonicDispatcher(tcalConsumer,
                        DataProcessor.StreamType.TCAL,
                        rapcal, mbid);

        //build the processors
        HitProcessor hitProcessor = new HitProcessor(mbid,
                config.getPedestalSubtraction(),
                config.isAtwdChargeStamp(),
                hitDispatch);

        DataProcessor.StreamProcessor supernovaProcessor =
                new SupernovaProcessor(mbid, supernovaDispatcher);

        DataProcessor.StreamProcessor moniProcessor =
                new MoniProcessor(mbid, moniDispatcher);


        DataProcessor.StreamProcessor tcalProcessor =
                new TCalProcessor(tcalDispatcher, mbid,
                        rapcal, gpsProvider);



        return new SynchronousDataProcessor(
                hitProcessor,
                supernovaProcessor,
                moniProcessor,
                tcalProcessor,
                rapcal,
                dataStats);
    }


    /**
     * Build the DataStats implementation
     */
    private static DataStats buildDataStats(final long mbid)
    {
        if(PRINT_VERBOSE_PROCESSING_STATS)
        {
            return new DataProcessingMonitor(mbid);
        }
        else
        {
            return new DataStats(mbid);
        }
    }


    /**
     * Instantiates a RAPCal instance based on configuration directive.
     */
    private static RAPCal instantiateRAPCal()
    {
        String rapcalClass = System.getProperty(
                "icecube.daq.domapp.datacollector.rapcal",
                "icecube.daq.rapcal.ZeroCrossingRAPCal"
        );
        try
        {
            return (RAPCal) Class.forName(rapcalClass).newInstance();
        }
        catch (Exception ex)
        {
            logger.warn("Unable to load / instantiate RAPCal class " +
                    rapcalClass + ".  Loading ZeroCrossingRAPCal instead.");
            return new ZeroCrossingRAPCal();
        }
    }

}

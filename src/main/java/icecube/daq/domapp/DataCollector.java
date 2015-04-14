/* -*- mode: java; indent-tabs-mode:f; tab-width:4 -*- */

package icecube.daq.domapp;

import icecube.daq.bindery.BufferConsumer;
import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.domapp.LocalCoincidenceConfiguration.RxMode;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.GPSService;
import icecube.daq.dor.IDriver;
import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.Alerter.Priority;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.rapcal.RAPCal;
import icecube.daq.rapcal.RAPCalException;
import icecube.daq.rapcal.ZeroCrossingRAPCal;
import icecube.daq.util.RealTimeRateMeter;
import icecube.daq.util.SimpleMovingAverage;
import icecube.daq.util.StringHubAlert;
import icecube.daq.util.UTC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.apache.log4j.Logger;

/**
 * Defines the DOM based data collector implementations, for use by
 * StringHubComponent and Omicron.
 *
 * This class hides the fact that we have more than one DataCollector
 * implementation available in the code base.
 *
 * @see LegacyDataCollector
 * @see NewDataCollector
 */
public abstract class DataCollector
    extends AbstractDataCollector
    implements DataCollectorMBean
{
    protected DataCollector(final int card, final int pair, final char dom)
    {
        super(card, pair, dom);
    }
}

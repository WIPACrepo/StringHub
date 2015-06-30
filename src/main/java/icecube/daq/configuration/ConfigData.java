package icecube.daq.configuration;

import icecube.daq.bindery.MultiChannelMergeSort;
import icecube.daq.bindery.PrioritySort;
import icecube.daq.bindery.SecondaryStreamConsumer;
import icecube.daq.common.DAQCmdInterface;
import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.bindery.ChannelSorter;
import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.domapp.DataCollector;
import icecube.daq.domapp.DataCollectorFactory;
import icecube.daq.domapp.MessageException;
import icecube.daq.domapp.RunLevel;
import icecube.daq.domapp.SimDataCollector;
import icecube.daq.dor.DOMChannelInfo;
import icecube.daq.dor.Driver;
import icecube.daq.dor.GPSService;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.OutputChannel;
import icecube.daq.io.PayloadReader;
import icecube.daq.io.SimpleOutputEngine;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.juggler.mbean.MemoryStatistics;
import icecube.daq.juggler.mbean.SystemStatistics;
import icecube.daq.livemoni.LiveTCalMoni;
import icecube.daq.monitoring.MonitoringData;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.ReadoutRequestFactory;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.priority.AdjustmentTask;
import icecube.daq.priority.SorterException;
import icecube.daq.sender.RequestReader;
import icecube.daq.sender.Sender;
import icecube.daq.util.DOMRegistry;
import icecube.daq.util.DeployedDOM;
import icecube.daq.util.FlasherboardConfiguration;
import icecube.daq.util.JAXPUtil;
import icecube.daq.util.JAXPUtilException;
import icecube.daq.util.StringHubAlert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

public class ConfigData
{
    private static final Logger logger = Logger.getLogger(ConfigData.class);

    /** configuration name to be used in error messages */
    private String name;

    public int tcalPrescale = 10;
    public boolean dcSoftboot;
    public boolean enable_intervals;
    public double snDistance = Double.NaN;
    public boolean forwardIsolatedHits;

    // hit spool configuration
    public boolean hitSpooling;
    public String hitSpoolDir;
    public long hitSpoolIval = 100000000000L; // Default 10s hit spool interval
    public int hitSpoolNumFiles = 100;

    private XMLConfig xmlConfig;

    public ConfigData(File configurationPath, String configName, int hubId)
        throws DAQCompException, JAXPUtilException
    {
        this.name = configName;

        // Parse out tags from 'master configuration' file
        Document doc = JAXPUtil.loadXMLDocument(configurationPath,
                                                configName);

        xmlConfig = new XMLConfig();

        final String snDistText =
            JAXPUtil.extractText(doc, "runConfig/setSnDistance");
        if (snDistText.length() > 0) {
            snDistance = Double.parseDouble(snDistText);
        }

        Node intvlNode =
            JAXPUtil.extractNode(doc, "runConfig/intervals/enabled");
        enable_intervals = parseIntervals(intvlNode, true);

        parseDOMConfig(configurationPath, doc, hubId);
    }

    public Set<String> getConfiguredDomIds()
    {
        return xmlConfig.getConfiguredDomIds();
    }

    public DOMConfiguration getDOMConfig(String mbid)
    {
        return xmlConfig.getDOMConfig(mbid);
    }

    public String getName()
    {
        return name;
    }

    public boolean isDOMIncluded(String mbid)
    {
        return xmlConfig.getDOMConfig(mbid) != null;
    }

    private void parseDOMConfig(File configurationPath, Document doc,
                                int hubId)
        throws DAQCompException, JAXPUtilException
    {
        File domConfigsDir = new File(configurationPath, "domconfigs");

        // Lookup <stringHub hubId='x'> node - if any - and process
        // configuration directives.
        final String hnPath = "runConfig/stringHub[@hubId='" + hubId +
            "']";
        Node hubNode = JAXPUtil.extractNode(doc, hnPath);
        if (hubNode == null) {
            parseDOMOldHubConfig(domConfigsDir, doc);
        } else {
            if (!readDOMConfig(domConfigsDir, hubNode, false)) {
                final String path = "runConfig/domConfigList[@hub='" +
                    hubId + "']";
                Node dclNode = JAXPUtil.extractNode(doc, path);

                if (dclNode == null ||
                    !readDOMConfig(domConfigsDir, dclNode, true))
                {
                    throw new DAQCompException("Cannot read DOM config" +
                                               " file for hub " + hubId);
                }

                parseDOMHubConfig(domConfigsDir, hubNode);

                Node hsNode = JAXPUtil.extractNode(hubNode, "hitspool");
                if (hsNode == null) {
                    // if there is no hitspool child of the stringHub tag
                    // look for a default node
                    hsNode = JAXPUtil.extractNode(doc, "runConfig/hitspool");
                }
                parseHitSpool(hsNode);
            }
        }
    }

    private void parseDOMHubConfig(File domConfigsDir, Node hubNode)
        throws JAXPUtilException
    {
        if (JAXPUtil.extractText(hubNode, "trigger/enabled").
            equalsIgnoreCase("true"))
        {
            logger.error("String triggering not implemented");
        }

        Node intvlNode = JAXPUtil.extractNode(hubNode, "intervals/enabled");
        enable_intervals = parseIntervals(intvlNode, enable_intervals);

        final String fwdProp = "sender/forwardIsolatedHitsToTrigger";
        final String fwdText = JAXPUtil.extractText(hubNode, fwdProp);
        forwardIsolatedHits = fwdText.equalsIgnoreCase("true");

        final String softProp = "dataCollector/softboot";
        final String softText =
            JAXPUtil.extractText(hubNode, softProp);
        if (softText.equalsIgnoreCase("true")) {
            dcSoftboot = true;
        }

        String tcalPStxt =
            JAXPUtil.extractText(hubNode, "tcalPrescale");
        if (tcalPStxt.length() != 0) {
            tcalPrescale = Integer.parseInt(tcalPStxt);
        }
    }

    private void parseDOMOldHubConfig(File domConfigsDir, Document doc)
        throws DAQCompException, JAXPUtilException
    {
        // handle older runconfig files which don't specify hubId
        NodeList dcList =
            JAXPUtil.extractNodeList(doc, "runConfig/domConfigList");

        if (dcList.getLength() > 0) {
            // handle really ancient runconfig files
            readAllDOMConfigs(domConfigsDir, dcList, true);
        } else {
            NodeList shList =
                JAXPUtil.extractNodeList(doc, "runConfig/stringhub");
            if (shList.getLength() > 0) {
                readAllDOMConfigs(domConfigsDir, shList, false);
            }
        }
    }

    private void parseHitSpool(Node hsNode)
        throws JAXPUtilException
    {
        hitSpooling=false;
        if (hsNode != null) {
            final String enabled =
                JAXPUtil.extractText(hsNode, "enabled");
            if (enabled.equalsIgnoreCase("true")) {
                hitSpooling = true;
            }

            hitSpoolDir = JAXPUtil.extractText(hsNode, "directory");
            if (hitSpoolDir.length() == 0) {
                hitSpoolDir = "/mnt/data/pdaqlocal";
            }

            final String hsIvalText =
                JAXPUtil.extractText(hsNode, "interval");
            if (hsIvalText.length() > 0) {
                final double interval = Double.parseDouble(hsIvalText);
                hitSpoolIval = (long) (1E10 * interval);
            }

            final String hsNFText =
                JAXPUtil.extractText(hsNode, "numFiles");
            if (hsNFText.length() > 0) {
                hitSpoolNumFiles  = Integer.parseInt(hsNFText);
            }
        }
    }

    /**
     * Parse the XML node to enable intervals.
     *
     * @param node 'interval' node (may be null)
     * @param prevValue previous value
     *
     * @return new value
     */
    private boolean parseIntervals(Node node, boolean prevValue)
    {
        boolean val;
        if (node == null) {
            val = prevValue;
        } else {
            val = node.getTextContent().equalsIgnoreCase("true");
        }

        return val;
    }

    /**
     * Read in DOM config info from run configuration file
     *
     * @param dir location of DOM configuration directory
     * @param nodeList list of DOM configuration nodes
     * @param oldFormat <tt>true</tt> if nodes are in old format
     *
     * @throws DAQCompException if a file cannot be read
     */
    private void readAllDOMConfigs(File dir, NodeList nodeList,
                                   boolean oldFormat)
        throws DAQCompException
    {
        for (int i = 0; i < nodeList.getLength(); i++) {
            readDOMConfig(dir, nodeList.item(i), oldFormat);
        }
    }

    /**
     * Read in DOM config info from run configuration file
     *
     * @param dir location of DOM configuration directory
     * @param nodeList list of DOM configuration nodes
     * @param oldFormat <tt>true</tt> if nodes are in old format
     *
     * @return <tt>true</tt> if the config file was read
     *
     * @throws DAQCompException if the file cannot be read
     */
    private boolean readDOMConfig(File dir, Node node, boolean oldFormat)
        throws DAQCompException
    {
        String tag;
        if (oldFormat) {
            tag = node.getTextContent();
        } else {
            tag = ((Element) node).getAttribute("domConfig");
            if (tag.equals("")) {
                return false;
            }
        }

        // add ".xml" if it's missing
        if (!tag.endsWith(".xml")) {
            tag = tag + ".xml";
        }

        // load DOM config
        File configFile = new File(dir, tag);
        if (logger.isDebugEnabled()) {
            logger.debug("Loading config from " +
                         configFile.getAbsolutePath());
        }

        FileInputStream in;
        try {
            in = new FileInputStream(configFile);
        } catch (FileNotFoundException fnfe) {
            throw new DAQCompException("Cannot open DOM config file " +
                                       configFile, fnfe);
        }

        try {
            xmlConfig.parseXMLConfig(in);
        } catch (Exception ex) {
            throw new DAQCompException("Cannot parse DOM config file " +
                                       configFile, ex);
        } finally {
            try {
                in.close();
            } catch (IOException ioe) {
                // ignore errors on close
            }
        }

        return true;
    }
}

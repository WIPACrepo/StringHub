package icecube.daq.configuration;

import icecube.daq.domapp.DOMConfiguration;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.stringhub.test.MockAppender;
import icecube.daq.util.DOMInfo;
import icecube.daq.util.DOMRegistryException;
import icecube.daq.util.DOMRegistryFactory;
import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.JAXPUtilException;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;

import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConfigDataTest
{
    private static final MockAppender appender = new MockAppender();

    private static final File CONFIGDIR =
        new File(ConfigDataTest.class.getResource("/config").getPath());

    private static final IDOMRegistry domRegistry;

    static {
        final String path = CONFIGDIR.getPath();
        try {
            domRegistry = DOMRegistryFactory.load(path);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new Error("Cannot initialize DOM registry from " + path, ex);
        }
    };

    private static final HashMap<Integer, String[]> HUB_TO_DOMS =
        new HashMap<Integer, String[]>() {{
            put(2, new String[] {
                    "0c188a421fad", // 02-01
                    "21537d5de371", // 02-60
                });
            put(1002, new String[] {
                    "53494d550201", // 1002-01
                    "53494d550260", // 1002-60
                });
        }};

    private void checkDOMs(ConfigData cfgData, int hubId, boolean isSim,
                           int pulserRate)
    {
/*
        System.err.println("All DOMs in " + cfgData.getName());
        for (String cfgDom : cfgData.getConfiguredDomIds()) {
            System.err.print(" " + cfgDom);
        }
        System.err.println();
*/

        // get the list of known DOMs for this hub
        String[] list = HUB_TO_DOMS.get(hubId);

        for (int i = 0; i < list.length; i++) {
            DOMConfiguration domCfg = cfgData.getDOMConfig(list[i]);
            if (domCfg == null) {
                fail("DOM " + list[i] + " not found in " + cfgData.getName());
            }

            if (domCfg.isSimulation()) {
                assertTrue(list[i] + " should be a simDOM", isSim);
                assertEquals("Bad noise rate for " + list[i],
                             50.0, domCfg.getSimNoiseRate(), 0.001);
            } else {
                assertFalse(list[i] + " should NOT be a simDOM", isSim);
                assertEquals("Bad pulser rate for " + list[i],
                             pulserRate, domCfg.getPulserRate());
            }
        }
    }

    @BeforeClass
    public static void initialize() throws Exception
    {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    @Test
    public void testAncientConfig()
        throws DAQCompException, JAXPUtilException
    {
        final int hubId = 2;

        Collection<DOMInfo> deployedDOMs;
        try {
            deployedDOMs = domRegistry.getDomsOnHub(hubId);
        } catch (DOMRegistryException dre) {
            throw new DAQCompException("Cannot get DOMs on hub " + hubId, dre);
        }

        ConfigData cfgData =
            new ConfigData(CONFIGDIR, "sps-IC2-remove-Sauron-V099", hubId,
                           deployedDOMs);
        checkDOMs(cfgData, hubId, false, 1);
    }

    @Test
    public void testStandardConfig()
        throws DAQCompException, JAXPUtilException
    {
        final int hubId = 2;

        Collection<DOMInfo> deployedDOMs;
        try {
            deployedDOMs = domRegistry.getDomsOnHub(hubId);
        } catch (DOMRegistryException dre) {
            throw new DAQCompException("Cannot get DOMs on hub " + hubId, dre);
        }

        ConfigData cfgData =
            new ConfigData(CONFIGDIR, "sps-IC2-message-packing-V235", hubId,
                           deployedDOMs);
        checkDOMs(cfgData, hubId, false, 2);
    }

    @Test
    public void testReplayConfig()
        throws DAQCompException, JAXPUtilException
    {
        final int hubId = 2;

        Collection<DOMInfo> deployedDOMs;
        try {
            deployedDOMs = domRegistry.getDomsOnHub(hubId);
        } catch (DOMRegistryException dre) {
            throw new DAQCompException("Cannot get DOMs on hub " + hubId, dre);
        }

        ConfigData cfgData =
            new ConfigData(CONFIGDIR, "replay-125659-local", hubId,
                           deployedDOMs);
        // checkDOMs(cfgData, hubId, false, 1); replay doesn't specify any DOMs
    }

    @Test
    public void testOldSimConfig()
        throws DAQCompException, JAXPUtilException
    {
        final int hubId = 1002;

        Collection<DOMInfo> deployedDOMs;
        try {
            deployedDOMs = domRegistry.getDomsOnHub(hubId);
        } catch (DOMRegistryException dre) {
            throw new DAQCompException("Cannot get DOMs on hub " + hubId, dre);
        }

        ConfigData cfgData =
            new ConfigData(CONFIGDIR, "sim2strIT-stdtest-01", hubId,
                           deployedDOMs);
        checkDOMs(cfgData, hubId, true, 1);
    }

    @Test
    public void testRandomConfig()
        throws DAQCompException, JAXPUtilException
    {
        final int hubId = 2;

        Collection<DOMInfo> deployedDOMs;
        try {
            deployedDOMs = domRegistry.getDomsOnHub(hubId);
        } catch (DOMRegistryException dre) {
            throw new DAQCompException("Cannot get DOMs on hub " + hubId, dre);
        }

        ConfigData cfgData =
            new ConfigData(CONFIGDIR, "random-01", hubId,
                           deployedDOMs);
        checkDOMs(cfgData, hubId, true, 1);
    }
}

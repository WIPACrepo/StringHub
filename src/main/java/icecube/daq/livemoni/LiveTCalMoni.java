package icecube.daq.livemoni;

import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.util.DeployedDOM;

import java.util.HashMap;

import org.apache.log4j.Logger;

public class LiveTCalMoni
{
    // these values are duplicated in icecube.daq.secBuilder.TCalAnalysis
    public static final String TCAL_EXCEPTION_NAME = "dom_tcalException";
    public static final int TCAL_EXCEPTION_VERSION = 0;

    private static final Logger LOG = Logger.getLogger(LiveTCalMoni.class);

    private Alerter alerter;
    private DeployedDOM domInfo;

    public LiveTCalMoni(Alerter alerter, DeployedDOM domInfo)
    {
        this.alerter = alerter;
        this.domInfo = domInfo;
    }

    // this method is a near-copy of icecube.daq.secBuiler.TCalAnalysis.send()
    public void send(String errmsg, TimeCalib tcal)
    {
        HashMap valueMap = new HashMap();
        valueMap.put("version", TCAL_EXCEPTION_VERSION);
        valueMap.put("string", domInfo.getStringMajor());
        valueMap.put("om", domInfo.getStringMinor());
        valueMap.put("error", errmsg);

        if (tcal != null) {
            tcal.addValues(valueMap);
        }

        try {
            alerter.send(TCAL_EXCEPTION_NAME, Alerter.Priority.SCP, valueMap);
        } catch (AlertException ae) {
            LOG.error("Cannot send " + TCAL_EXCEPTION_NAME, ae);
        }
    }
}

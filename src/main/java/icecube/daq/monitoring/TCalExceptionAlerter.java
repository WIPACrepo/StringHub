package icecube.daq.monitoring;

import icecube.daq.dor.TimeCalib;
import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.AlertQueue;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.util.DeployedDOM;

import java.util.HashMap;

import org.apache.log4j.Logger;

public class TCalExceptionAlerter
{
    public static final String TCAL_EXCEPTION_NAME = "dom_tcalException";
    public static final int TCAL_EXCEPTION_VERSION = 0;

    private static final Logger LOG =
        Logger.getLogger(TCalExceptionAlerter.class);

    private AlertQueue alertQueue;
    private DeployedDOM domInfo;

    public TCalExceptionAlerter(AlertQueue alertQueue, DeployedDOM domInfo)
    {
        this.alertQueue = alertQueue;
        this.domInfo = domInfo;
    }

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
            alertQueue.push(TCAL_EXCEPTION_NAME, Alerter.Priority.SCP,
                            valueMap);
        } catch (AlertException ae) {
            LOG.error("Cannot send " + TCAL_EXCEPTION_NAME, ae);
        }
    }
}

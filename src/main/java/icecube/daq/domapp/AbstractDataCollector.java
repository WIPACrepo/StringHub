package icecube.daq.domapp;

/**
 * Generic interface to a DOM data collector.
 * A real DOM data collector does the following things:
 * <ul>
 * <li>Configures the DOM given a configuration class,</li>
 * <li>Collects data from the <b>hit</b>, <b>monitor</b>,
 * <b>supernova</b>, and <b>tcal</b> sources and (optionally)
 * sends the data out on channels supplied by the 'caller.'</li>
 * </ul> 
 * @author krokodil
 *
 */
public abstract class AbstractDataCollector extends Thread {
	public abstract void setConfig(DOMConfiguration config);
	public abstract void signalConfigure();
	public abstract void signalStartRun();
	public abstract void signalStopRun();
	public abstract void signalShutdown();
	public abstract int  queryDaqRunLevel();
}

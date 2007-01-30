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
	public static final int IDLE = 0;
	public static final int CONFIGURING = 1;
	public static final int CONFIGURED = 2;
	public static final int STARTING = 3;
	public static final int RUNNING = 4;
	public static final int STOPPING = 5;

	public abstract void setConfig(DOMConfiguration config);
	public abstract void signalConfigure();
	public abstract void signalStartRun();
	public abstract void signalStopRun();
	public abstract void signalShutdown();
	public abstract int  queryDaqRunLevel();
}

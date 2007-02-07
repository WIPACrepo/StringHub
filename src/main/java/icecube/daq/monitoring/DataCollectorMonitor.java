
package icecube.daq.monitoring;
import icecube.daq.stringhub.DOMConnector;
import icecube.daq.domapp.AbstractDataCollector;
import java.util.List;
import java.util.Collection;

public class DataCollectorMonitor implements DataCollectorMonitorMBean
{
	private DOMConnector conn;

	public DataCollectorMonitor() {
		this.conn = null;
	}

	public void setConnector(DOMConnector conn) {
		this.conn = conn;
	}

	public long[] getNumHits() {
		if (conn == null) return new long[0];
		List<AbstractDataCollector> collectors = conn.getCollectors();
		int n = collectors.size();
		long[] hits = new long[n];
		int i=0;
		for (AbstractDataCollector adc : collectors)
			hits[i++] = adc.getNumHits();
		return hits;
	}

	public long[] getNumMoni() {
		if (conn == null) return new long[0];
		List<AbstractDataCollector> collectors = conn.getCollectors();
		int n = collectors.size();
		long[] moni = new long[n];
		int i=0;
		for (AbstractDataCollector adc : collectors)
			moni[i++] = adc.getNumMoni();
		return moni;
	}

	public long[] getNumTcal() {
		if (conn == null) return new long[0];
		List<AbstractDataCollector> collectors = conn.getCollectors();
		int n = collectors.size();
		long[] tcal = new long[n];
		int i=0;
		for (AbstractDataCollector adc : collectors)
			tcal[i++] = adc.getNumTcal();
		return tcal;
	}
	
	public long[] getNumSupernova() {
		if (conn == null) return new long[0];
		List<AbstractDataCollector> collectors = conn.getCollectors();
		int n = collectors.size();
		long[] sn = new long[n];
		int i=0;
		for (AbstractDataCollector adc : collectors)
			sn[i++] = adc.getNumSupernova();
		return sn;
	}

	public String[] getRunLevel() {
		if (conn == null) return new String[0];
		List<AbstractDataCollector> collectors = conn.getCollectors();
		int n = collectors.size();
		String[] lev = new String[n];
		int i=0;
		for (AbstractDataCollector adc : collectors)
			lev[i++] = adc.getRunLevel();
		return lev;
	}
	
	public long[] getAcqusitionLoopCount() {
		if (conn == null) return new long[0];
		List<AbstractDataCollector> collectors = conn.getCollectors();
		int n = collectors.size();
		long[] counters = new long[n];
		int i=0;
		for (AbstractDataCollector adc : collectors) {
			counters[i++] = adc.getAcquisitionLoopCount();
		}
		return counters;
	}
}



			

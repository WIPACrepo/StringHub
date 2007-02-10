
package icecube.daq.monitoring;

import icecube.daq.stringhub.DOMConnector;
import icecube.daq.domapp.AbstractDataCollector;
import icecube.daq.bindery.StreamBinder;
import java.util.List;
import java.util.Collection;

public class DataCollectorMonitor implements DataCollectorMonitorMBean
{
	private DOMConnector conn;
	private StreamBinder hitsBinder;
	private StreamBinder moniBinder;
	private StreamBinder tcalBinder;
	private StreamBinder supernovaBinder;

        public DataCollectorMonitor() {
		this.conn = null;
		this.hitsBinder = null;
		this.moniBinder = null;
		this.tcalBinder = null;
		this.supernovaBinder = null;
	}

	public void setConnector(DOMConnector conn) {
		this.conn = conn;
	}

	public void setBinders(StreamBinder hits, StreamBinder moni, StreamBinder tcal, StreamBinder supernova) {
		this.hitsBinder = hits;
		this.moniBinder = moni;
		this.tcalBinder = tcal;
		this.supernovaBinder = supernova;
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

	public long getHitsHKN1Counter() {
		if (hitsBinder == null) return 0;
		return hitsBinder.getCounter();
	}
	public long getHitsHKN1InputCounter() {
		if (hitsBinder == null) return 0;
		return hitsBinder.getCounter();
	}
	public long getHitsHKN1OutputCounter() {
		if (hitsBinder == null) return 0;
		return hitsBinder.getCounter();
	}

	public long getMoniHKN1Counter() {
		if (moniBinder == null) return 0;
		return moniBinder.getCounter();
	}
	public long getMoniHKN1InputCounter() {
		if (moniBinder == null) return 0;
		return moniBinder.getCounter();
	}
	public long getMoniHKN1OutputCounter() {
		if (moniBinder == null) return 0;
		return moniBinder.getCounter();
	}

	public long getTcalHKN1Counter() {
		if (tcalBinder == null) return 0;
		return tcalBinder.getCounter();
	}
	public long getTcalHKN1InputCounter() {
		if (tcalBinder == null) return 0;
		return tcalBinder.getCounter();
	}
	public long getTcalHKN1OutputCounter() {
		if (tcalBinder == null) return 0;
		return tcalBinder.getCounter();
	}

	public long getSupernovaHKN1Counter() {
		if (supernovaBinder == null) return 0;
		return supernovaBinder.getCounter();
	}
	public long getSupernovaHKN1InputCounter() {
		if (supernovaBinder == null) return 0;
		return supernovaBinder.getCounter();
	}
	public long getSupernovaHKN1OutputCounter() {
		if (supernovaBinder == null) return 0;
		return supernovaBinder.getCounter();
	}

	public long getLastHKN1HitTime() {
		if (hitsBinder == null) return 0L;
		return hitsBinder.getLastUT();
	}

	public long getLastHKN1MoniTime() {
		if (moniBinder == null) return 0L;
		return moniBinder.getLastUT();
	}

	public long getLastHKN1TcalTime() {
		if (tcalBinder == null) return 0L;
		return tcalBinder.getLastUT();
	}

	public long getLastHKN1SupernovaTime() {
		if (supernovaBinder == null) return 0L;
		return supernovaBinder.getLastUT();
	}
	
}



			

package icecube.daq.dor;

public class DOMChannelInfo {

	public String mbid;
	public int card;
	public int pair;
	public char dom;

	public DOMChannelInfo(String mbid, int card, int pair, char dom) {
		this.mbid = mbid;
		this.card = card;
		this.pair = pair;
		this.dom  = dom;
	}

}

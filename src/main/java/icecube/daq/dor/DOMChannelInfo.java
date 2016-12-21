package icecube.daq.dor;

public class DOMChannelInfo {

	public String mbid;
	public int card;
	public int pair;
	public char dom;
	public long mbid_numerique;

	public DOMChannelInfo(String mbid, int card, int pair, char dom) {
		this(mbid, Long.parseLong(mbid, 16), card, pair, dom);
	}

	public DOMChannelInfo(String mbid, long mbid_numerique,int card,
				int pair, char dom)
	{
		this.mbid = mbid;
		this.mbid_numerique = mbid_numerique;
		this.card = card;
		this.pair = pair;
		this.dom  = dom;
	}

	public long getMainboardIdAsLong()
	{
	    return mbid_numerique;
	}

	@Override
	public String toString()
	{
	    return card + "" + pair + "" + dom;
	}
}

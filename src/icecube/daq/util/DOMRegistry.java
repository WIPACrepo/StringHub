package icecube.daq.util;

import java.io.InputStream;
import java.util.HashMap;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * The DOM registry is a utility class for looking up DOM information.
 * @author krokodil
 *
 */
public class DOMRegistry extends DefaultHandler
{
	private Throwable failure;
	private StringBuffer xmlChars;
	private HashMap<String, InfoTuple> byMbid;
	private InfoTuple tuple;
	private static final DOMRegistry instanceObject = new DOMRegistry();
	
	protected DOMRegistry()
	{
		failure = null;
		byMbid = new HashMap<String, InfoTuple>();
		try
		{
			InputStream xmlIn = DOMRegistry.class.getResourceAsStream("dom-registry.xml");
			SAXParserFactory saxFactory = SAXParserFactory.newInstance();
			saxFactory.setNamespaceAware(true);
			SAXParser parser = saxFactory.newSAXParser();
			parser.parse(xmlIn, this);
		}
		catch (Exception exception)
		{
			failure = exception;
		}
	}
	
	/**
	 * Lookup DOM Id given mainboard Id
	 * @param mbid input DOM mainboard id - the 12-char hex
	 * @return 8-char DOM Id - like TP5Y0515
	 */
	public String getDomId(String mbid)
	{
		return byMbid.get(mbid).domid;
	}
	
	/**
	 * Lookup Krasberg name of DOM given mainboard Id.
	 * @param mbid input DOM mainboard id.
	 * @return DOM name
	 */
	public String getName(String mbid)
	{
		return byMbid.get(mbid).name;
	}
	
	public int getString(String mbid)
	{
		return byMbid.get(mbid).string;
	}
	
	public int getPosition(String mbid)
	{
		return byMbid.get(mbid).position;
	}

	public String getDeploymentLocation(String mbid)
	{
		return String.format("%2.2d-%2.2d", getString(mbid), getPosition(mbid));
	}
	
	public DOMRegistry getInstance()
	{ 
		if (instanceObject.failure != null) throw new IllegalStateException(failure);
		return instanceObject;
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException 
	{
		xmlChars.append(ch, start, length);
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException 
	{
		String text = xmlChars.toString().trim();
		if (localName.equals("mbid"))
			tuple.mbid = text;
		else if (localName.equals("domid"))
			tuple.domid = text;
		else if (localName.equals("name"))
			tuple.name = text;
		else if (localName.equals("string"))
			tuple.string = Integer.parseInt(text);
		else if (localName.equals("position"))
			tuple.position = Integer.parseInt(text);
		else if (localName.equals("dom"))
			byMbid.put(tuple.mbid, new InfoTuple(tuple));
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException 
	{
		xmlChars.setLength(0);
	}
	
}

class InfoTuple
{
	String mbid;
	String domid;
	String name;
	int string;
	int position;
	
	InfoTuple(InfoTuple copyFrom)
	{
		this.mbid = copyFrom.mbid;
		this.domid = copyFrom.domid;
		this.name = copyFrom.name;
		this.string = copyFrom.string;
		this.position = copyFrom.position;
	}

}
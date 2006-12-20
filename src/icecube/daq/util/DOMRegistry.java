package icecube.daq.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
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
	private StringBuffer xmlChars;
	private boolean isInitialized;
	private HashMap<String, DeployedDOM> doms;
	private DeployedDOM currentDOM;
	private static final String DEFAULT_DOM_GEOMETRY = "default-dom-geometry.xml";
	
	protected DOMRegistry()
	{
		xmlChars = new StringBuffer();
		isInitialized = false;
		currentDOM = new DeployedDOM();
		doms = new HashMap<String, DeployedDOM>();
	}
	
	public static DOMRegistry loadRegistry(String path) throws 
	ParserConfigurationException, 
	SAXException, IOException
	{ 
		File file = new File(path, DEFAULT_DOM_GEOMETRY);
		FileInputStream is = new FileInputStream(file);
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		SAXParser parser = factory.newSAXParser();
		DOMRegistry reg = new DOMRegistry();
		parser.parse(is, reg);
		reg.isInitialized = true;
		return reg;
	}	
	
	/**
	 * Lookup DOM Id given mainboard Id
	 * @param mbid input DOM mainboard id - the 12-char hex
	 * @return 8-char DOM Id - like TP5Y0515
	 */
	public String getDomId(String mbid)
	{
		return doms.get(mbid).domId;
	}
	
	/**
	 * Lookup Krasberg name of DOM given mainboard Id.
	 * @param mbid input DOM mainboard id.
	 * @return DOM name
	 */
	public String getName(String mbid)
	{
		return doms.get(mbid).name;
	}
	
	public int getStringMajor(String mbid)
	{
		return doms.get(mbid).getStringMajor();
	}
	
	public int getStringMinor(String mbid)
	{
		return doms.get(mbid).getStringMinor();
	}

	public String getDeploymentLocation(String mbid)
	{
		DeployedDOM dom = doms.get(mbid);
		return String.format("%2.2d-%2.2d", dom.string, dom.location);
	}

	public ArrayList<DeployedDOM> getDomsOnString(int string)
	{
		ArrayList<DeployedDOM> rlist = new ArrayList<DeployedDOM>(60);
		for (DeployedDOM dom : doms.values()) if (string == dom.string) rlist.add(dom); 
		return rlist;
	}
	
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException 
	{
		super.characters(ch, start, length);
		xmlChars.append(ch, start, length);
	}

	@Override
	public void startElement(String uri, String localName, String qName, 
			Attributes attributes) throws SAXException 
	{
		super.startElement(uri, localName, qName, attributes);
		xmlChars.setLength(0);
	}

	@Override
	public void endElement(String uri, String localName, String qName) 
	throws SAXException 
	{
		super.endElement(uri, localName, qName);
		String txt = xmlChars.toString().trim();
		if (localName.equals("dom"))
			doms.put(currentDOM.mainboardId, new DeployedDOM(currentDOM));
		else if (localName.equals("position"))
			currentDOM.location = Integer.parseInt(txt);
		else if (localName.equals("mainBoardId"))
			currentDOM.mainboardId = txt;
		else if (localName.equals("name"))
			currentDOM.name = txt;
		else if (localName.equals("productionId"))
			currentDOM.domId = txt;
		else if (localName.equals("xCoordinate"))
			currentDOM.x = Double.parseDouble(txt);
		else if (localName.equals("yCoordinate"))
			currentDOM.y = Double.parseDouble(txt);
		else if (localName.equals("zCoordinate"))
			currentDOM.z = Double.parseDouble(txt);
		else if (localName.equals("number"))
			currentDOM.string = Integer.parseInt(txt);
	}
}

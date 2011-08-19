package icecube.daq.configuration;

import icecube.daq.dor.DOMChannelInfo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class SimConfig
{
    private Document doc;

    public static SimConfig parseXML(InputStream inputStream) 
        throws DocumentException
    {
        SimConfig simConfig = new SimConfig();
        SAXReader saxRead = new SAXReader();
        simConfig.doc = saxRead.read(inputStream);
        return simConfig;
    }

    public List<DOMChannelInfo> getActiveDOMs()
    {
        ArrayList<DOMChannelInfo> doms = new ArrayList<DOMChannelInfo>(60);
        List nodes = doc.selectNodes("simulation/activeDOMs/simDOM");
        Iterator it = nodes.iterator();
        while (it.hasNext()) {
            Element domSim = (Element) it.next();
            String mbid = domSim.element("mbid").getTextTrim();
            int card = Integer.parseInt(domSim.element("card").getTextTrim());
            int pair = Integer.parseInt(domSim.element("pair").getTextTrim());
            char dom = domSim.element("dom").getTextTrim().charAt(0);

            doms.add(new DOMChannelInfo(mbid, card, pair, dom));
        }
        return doms;
    }

    public double getNoiseRate(String mbid)
    {
        Element noise = (Element) doc.selectSingleNode(
            "simulation/activeDOMs/simDOM[@mbid='" + mbid + "']/noiseRate");
        return Double.parseDouble(noise.getText());
    }
}

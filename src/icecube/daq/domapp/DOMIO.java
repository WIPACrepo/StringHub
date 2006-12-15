package icecube.daq.domapp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.log4j.Logger;

public class DOMIO {
	
	private int card;
	private int pair;
	private char dom;
	private FileChannel channel;
	private ByteBuffer in;
	private static Logger logger = Logger.getLogger(DOMIO.class);
	
	public DOMIO(int card, int pair, char dom) throws FileNotFoundException {
		this.card = card;
		this.pair = pair;
		this.dom  = dom;
		
		File devfile = new File("/dev/dhc" + card + 'w' + pair + 'd' + dom);
		RandomAccessFile file = new RandomAccessFile(devfile, "rws");
		channel = file.getChannel();
		
		// TODO - hack to 4092 - make this better
		// TODO - do we want Direct or Indirect?
		in = ByteBuffer.allocateDirect(4092);
	}
	
	/**
	 * Send message to DOMApp
	 * @param buf - output buffer going to DOMApp
	 * @return - # of bytes written to the device
	 * @throws IOException
	 */
	public int send(ByteBuffer buf) throws IOException {
		int nw = channel.write(buf);
		logger.debug("ch=(" + card + ", " + pair + ", " + dom + ") xmit " + nw + " bytes to DOM.");
		return nw;
	}
	
	/**
	 * Receive message from DOMApp 
	 * @return buffer of read message.  Note this buffer is used internally by
	 * DOMIO and will be overwritten on the next call to recv.
	 * @throws IOException
	 */
	public ByteBuffer recv() throws IOException {
		in.clear();
		int nr = channel.read(in);
		logger.debug("ch=(" + card + ", " + pair + ", " + dom + ") read " + nr + " bytes from DOM.");
		in.flip();
		return in;
	}
}

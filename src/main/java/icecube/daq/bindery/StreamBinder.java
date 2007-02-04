package icecube.daq.bindery;

import icecube.daq.util.UTC;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

import org.apache.log4j.Logger;

public class StreamBinder extends Thread implements Counter {
	
	private ArrayList<Node<DAQRecord>> inputs;
	private Selector 			selector;
	private int 				nreg;
	private BufferConsumer		out;
	private Node<DAQRecord> 	terminal;
	private ArrayList<Node<?>>	allNodes;
	private static final Logger logger = Logger.getLogger(StreamBinder.class);
	private boolean 			running;
	private static final ByteBuffer eos;
	private int					counter;
	private int					counterMax;
	
	static {
		eos = ByteBuffer.allocate(32);
		eos.putInt(32).putInt(0).putLong(0).putInt(0).putInt(0).putLong(Long.MAX_VALUE);
		eos.flip();

	}
	
	public StreamBinder(int n, BufferConsumer out) throws IOException
	{
		this(n, out, "hits");
	}

	public StreamBinder(int n, BufferConsumer out, String bindType) throws IOException 
	{
		super("StreamBinder" + "-" + bindType);
		
		inputs = new ArrayList<Node<DAQRecord>>();
		allNodes = new ArrayList<Node<?>>();
		
		Comparator<DAQRecord> cmp = new DAQRecordComparator();
		for (int i = 0; i < n; i++) inputs.add(new Node<DAQRecord>(cmp, this));
		nreg = 0;
		terminal = Node.makeTree(inputs, cmp, this);
		this.out = out;
		selector = Selector.open();
		running = false;
		counter = 0;
		counterMax = Integer.getInteger("icecube.daq.bindery.StreamBinder.populationLimit", 100000);
	}
	
	public void register(SelectableChannel ch) throws IOException {
		register(ch, null);
	}
	
	public void register(SelectableChannel ch, String streamName) throws IOException {
		if (nreg == inputs.size()) throw new IllegalStateException("Too many input channels registered");
		Node<DAQRecord> node = inputs.get(nreg++);
		node.setName(streamName);
		StreamInputNode sin = new StreamInputNode(node);
		ch.configureBlocking(false);
		SelectionKey key = ch.register(selector, SelectionKey.OP_READ);
		key.attach(sin);
	}
	
	public synchronized void shutdown() { running = false; }
	
	public void run() {
	
		UTC lastUT = new UTC();
	
		running = true;
		
		while (running) 
		{
			try 
			{
				int n = selector.select(500);
				if (logger.isDebugEnabled())
					logger.debug("Selector returned " + n + " interests; counter = " + counter);
				for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) 
				{
					SelectionKey key = it.next();
					it.remove();
					logger.debug("Sort tree object count = " + counter);
					// overflow handling - check whether the counter is too large
					if (counter > counterMax)
					{
						// TODO stop incoming data or do something
					}
					StreamInputNode node = (StreamInputNode) key.attachment();
					ReadableByteChannel ch = (ReadableByteChannel) key.channel();
					node.readRecords(ch);
					while (!terminal.isEmpty()) 
					{
						DAQRecord rec = terminal.head();
						UTC currentUT = rec.time();
						if (currentUT.compareTo(lastUT) < 0)
							logger.warn(getName() + " out-of-order record detected");
						// A single end-of-stream is sufficient to shut down this binder.
						if (logger.isDebugEnabled())
							logger.debug(getName() + "sending buffer to sender RECL = " +
										 rec.getBuffer().getInt(0) + " - TYPE = " + 
										 rec.getBuffer().getInt(4) + " - UTC = " + currentUT.toString());
						if (rec.getBuffer().getInt(0) == 32 
								&& rec.getBuffer().getLong(8) == 0L 
								&& rec.getBuffer().getLong(24) == Long.MAX_VALUE) running = false;
						while (rec.getBuffer().remaining() > 0) 
						{
							ByteBuffer buf = rec.getBuffer();
							out.consume(buf);
						}
						terminal.pop();
						// Update the lastUT
						lastUT = currentUT;
					}
				}
			} 
			catch (IOException iox) 
			{
				iox.printStackTrace();
				break;
			}
		}
	}
	
	/**
	 * This static method will return the end-of-stream token (a special 32-byte ByteBuffer).
	 * @return
	 */
	public static ByteBuffer endOfStream() { 
		eos.putInt(4, 0);
		return eos.asReadOnlyBuffer(); 
	}

	public static ByteBuffer endOfMoniStream() { 
		eos.putInt(4, 102);
		return eos.asReadOnlyBuffer(); 
	}

	public static ByteBuffer endOfTcalStream() { 
		eos.putInt(4, 202);
		return eos.asReadOnlyBuffer(); 
	}

	public static ByteBuffer endOfSupernovaStream() { 
		eos.putInt(4, 302);
		return eos.asReadOnlyBuffer(); 
	}

	public void dec() {
		counter--;
	}

	public int getCount() {
		return counter;
	}

	public void inc() {
		counter++;
	}

	public boolean overflow() {
		return counter > counterMax;
	}

	public void announce(Node<?> node) 
	{
		allNodes.add(node);
	}
	
	public void reset()
	{
		for (Node<?> node : allNodes)
			node.clear();
		counter = 0;
	}
	
}

class DAQRecordComparator implements Comparator<DAQRecord> {

	public int compare(DAQRecord o1, DAQRecord o2) {
		UTC t1 = o1.time();
		UTC t2 = o2.time();
		return t1.compareTo(t2);
	}
	
}

/**
 * Class for handling DAQ records.  Reads a record from a
 * supplied byte channel and stuffs the complete record
 * into the Node.
 * @author krokodil
 *
 */
class StreamInputNode {
	
	private Node<DAQRecord> node;
	private ByteBuffer iobuf;
	private static Logger logger = Logger.getLogger(StreamInputNode.class);
	
	public StreamInputNode(Node<DAQRecord> node) {
		iobuf = ByteBuffer.allocateDirect(10000);
		this.node = node;
	}
	
	public String getName() { return node.getName(); }
	
	/**
	 * This method reads bytes from the input channel.  It
	 * will push into the associated node the byte buffer
	 * holding a complete record if such is available.
	 * @param ch - input channel
	 * @throws IOException
	 */
	public void readRecords(ReadableByteChannel ch) throws IOException 
	{
		int nr = ch.read(iobuf);
		
		iobuf.flip();

		while (iobuf.remaining() >= 4)
		{
			int pos  = iobuf.position();
			int recl = iobuf.getInt(pos);
			assert recl >= 32;
			if (logger.isDebugEnabled())
				logger.debug(getName() + " : parsing " + recl + "-byte record @ pos = " + pos);
			if (iobuf.remaining() < recl) break;
			ByteBuffer buf = ByteBuffer.allocate(recl);
			int limit = iobuf.limit();
			iobuf.limit(pos + recl);
			buf.put(iobuf).flip();
			iobuf.limit(limit);
			node.push(new DAQRecord(buf));
		}
		
		// done accessing this bank of records - compact
		// the buffer in preparation for next read
		iobuf.compact();
		
	}
	
}

class DAQRecord implements Timestamped {
	
	private ByteBuffer buf;
	private UTC utc;
	public DAQRecord(ByteBuffer buf) {
		this.buf = buf;
		this.utc = new UTC(buf.getLong(24));
	}
	
	public UTC time() { return utc;	}
	
	public ByteBuffer getBuffer() { return buf; }
	
}

package icecube.daq.bindery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cern.jet.random.Exponential;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

import icecube.daq.common.MockAppender;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Test;

public class StreamBinderGeneratorTest {

	private static final Logger logger = Logger.getLogger(StreamBinderGeneratorTest.class);
	private static final MockAppender appender = new MockAppender();

	public StreamBinderGeneratorTest() {
		BasicConfigurator.resetConfiguration();
		BasicConfigurator.configure(appender);
		//appender.setVerbose(true).setLevel(Level.INFO);
	}

	@After
	public void tearDown() throws Exception
	{
		appender.assertNoLogMessages();
	}

	/**
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void testFull() throws IOException, InterruptedException {

		int ngen = 2;
		double simulationTime = 1.0;
		int outputCounter = 0;
		Pipe pipe = Pipe.open();
		pipe.sink().configureBlocking(false);
                BufferConsumerChannel out =
                    new BufferConsumerChannel(pipe.sink());
		StreamBinder bind = new StreamBinder(ngen, out);
		for (int i = 0; i < ngen; i++) {
			Pipe p = Pipe.open();
			p.source().configureBlocking(false);
			bind.register(p.source(), "G" + i);
			Generator gen = new Generator(i+10, p.sink(), simulationTime, 300.0);
			gen.start();
		}
		bind.start();
		logger.info("Starting event sequence.");
		ByteBuffer buf = ByteBuffer.allocate(100000);
		boolean stop = false;
		long last_time = 0;
		while (!stop) {
			int nr = pipe.source().read(buf);
			if (logger.isDebugEnabled())
				logger.debug("Read " + nr + " bytes from pipe source.");
			while (true) {
				int pos = buf.position();
				if (pos < 4) break;
				int len = buf.getInt(0);
				if (pos < len) break;
				if (logger.isDebugEnabled())
					logger.debug("At output: pos =  " + pos + " len = " + len +
						" : buffer id = " + buf.getLong(8) + ", time " + buf.getLong(24)
						);
				buf.limit(buf.position());
				outputCounter++;
				long time = buf.getLong(24);
				if (logger.isDebugEnabled())
					logger.debug("Time: " + time);
				if (time < last_time)
					logger.error("Time ordering error time = " + time + " last = " + last_time);
				assertTrue(time >= last_time);
				last_time = time;
				if (time == Long.MAX_VALUE) {
					logger.info("Caught end-of-stream-signal - stopping.");
					stop = true;
					break;
				}
				buf.position(len);
				buf.compact();
			}
		}

		bind.shutdown();

	}

}

class Generator extends Thread {
	private Pipe.SinkChannel sink;
	private double time;
	private long id;
	private static RandomEngine engine = new MersenneTwister(new java.util.Date());
	private Exponential rv;
	private static Logger logger = Logger.getLogger(Generator.class);
	private double max_time;

	Generator(long id, Pipe.SinkChannel sink, double max_time, double rate) {
		this.sink = sink;
		this.id   = id;
		rv = new Exponential(rate, engine);
		time = 0;
		setName("Generator (" + id + ")");
		this.max_time = max_time;
	}

	public void run() {
		while (time < max_time) {
			try {
				fireEvent();
				if (logger.isDebugEnabled())
					logger.debug("fired event - current time is " + time);
			} catch (IOException iox) {
				iox.printStackTrace();
			} catch (InterruptedException intx) {
				intx.printStackTrace();
			}
		}

		try {
			int nw = sink.write(StreamBinder.endOfStream());
			if (logger.isInfoEnabled())
				logger.info("Wrote end-of-stream - " + nw + " bytes.");
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	void fireEvent() throws IOException, InterruptedException {
		ByteBuffer buf = ByteBuffer.allocate(4092);
		double delay = 0.0;
		while (buf.remaining() >= 32) {
			double dt = rv.nextDouble();
			time += dt;
			delay += dt;
			if (logger.isDebugEnabled())
				logger.debug("New generator hit at delta time = " + dt + " time = " + time);
			buf.putInt(32).putInt(601).putLong(id);
			buf.putInt(0).putInt(0).putLong((long) (time*1.0E+10));
		}
		if (logger.isDebugEnabled())
			logger.debug("Delay = " + delay + " time = " + time);
		Thread.sleep((long) (1000.0 * delay));
		buf.flip();
		int nw = sink.write(buf);
		if (logger.isDebugEnabled())
			logger.debug("Wrote " + nw + " bytes.");
	}
}

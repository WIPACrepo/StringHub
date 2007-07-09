package icecube.daq.stringhub;

import icecube.daq.common.DAQCmdInterface;
import icecube.daq.juggler.component.DAQCompServer;
import icecube.daq.juggler.component.DAQComponent;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.MasterPayloadFactory;
import icecube.daq.payload.VitreousBufferCache;

public class MockTriggerBridge extends DAQComponent {

	private IByteBufferCache genericCacheManager;
	private MasterPayloadFactory masterPayloadFactory;
	private int runNumber;
	
	public MockTriggerBridge() 
	{
		super(DAQCmdInterface.DAQ_GLOBAL_TRIGGER, 0);
		
		genericCacheManager = new VitreousBufferCache();
		addCache(genericCacheManager);
		
		masterPayloadFactory = new MasterPayloadFactory(genericCacheManager);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception 
	{
		new DAQCompServer(new MockTriggerBridge(), args);
	}

}

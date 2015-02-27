package icecube.daq.replay;

import java.nio.ByteBuffer;

public interface HandlerOutputProcessor
{
    void send(ByteBuffer buf);
    void stop();
}

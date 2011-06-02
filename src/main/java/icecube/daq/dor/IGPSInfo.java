package icecube.daq.dor;

import icecube.daq.util.UTC;

import java.nio.ByteBuffer;

public interface IGPSInfo
{
    int getDay();
    int getHour();
    int getMin();
    int getSecond();
    int getQuality();
    UTC getOffset();
    ByteBuffer getBuffer();
}

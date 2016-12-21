package icecube.daq.time.gps.test;

import icecube.daq.dor.GPSException;
import icecube.daq.dor.GPSInfo;
import icecube.daq.dor.test.MockDriverBase;

import java.io.File;

/**
 * Mocks the GPS related driver code.
 */
public class MockGPSDriver extends MockDriverBase
{

    GPSInfo value;
    GPSException exception;

    public enum Mode
    {
        Value, Producer, Exception
    }
    Mode mode = Mode.Value;

    public static interface Producer
    {
        public GPSInfo readGPS(final File gpsFile) throws GPSException;
    }
    Producer producer;

    public void setMode(final Mode mode)
    {
        this.mode = mode;
    }

    public void setValue(final GPSInfo value)
    {
        this.value = value;
    }

    public void setProducer(final Producer producer)
    {
        this.producer = producer;
    }

    public void setException(GPSException exception)
    {
        this.exception = exception;
    }

    @Override
    public File getGPSFile(final int card)
    {
        return null;
    }

    @Override
    public GPSInfo readGPS(final File gpsFile) throws GPSException
    {
        switch (mode)
        {
            case Value:
                return value;
            case Producer:
                return producer.readGPS(gpsFile);
            case Exception:
                throw exception;
            default:
                throw new Error("Impossible?");
        }
    }
}

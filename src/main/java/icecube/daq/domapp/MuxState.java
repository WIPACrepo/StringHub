package icecube.daq.domapp;

public enum MuxState 
{
    OFF(-1), OSC_OUTPUT(0), SQUARE_40MHZ(1), LED_CURRENT(2),
    FB_CURRENT(3), UPPER_LC(4), LOWER_LC(5), COMM_ADC_INPUT(6), FE_PULSER(7);
    private byte mode;
    MuxState(int mode) 
    {
        this.mode = (byte) mode;
    }
    byte getValue() 
    { 
        return mode; 
    }
}

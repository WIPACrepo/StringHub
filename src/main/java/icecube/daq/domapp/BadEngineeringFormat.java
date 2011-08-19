package icecube.daq.domapp;

public class BadEngineeringFormat extends Exception 
{
    private static final long serialVersionUID = 1L;
    private int size;
    private int samples;

    public BadEngineeringFormat(int size, int samples) 
    {
        this.size = size; this.samples = samples;
    }

    public String toString() 
    {
        return "(" + size + ", " + samples + ")";
    }

}

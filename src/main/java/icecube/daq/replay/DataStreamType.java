package icecube.daq.replay;

/**
 * Replay data stream types.
 */
enum DataStreamType
{
    HIT("hit", 0),
    MONI("moni", 1),
    SN("sn", 2),
    TCAL("tcal", 3);

    private final String filename;
    private final int index;

    /**
     * Create a data stream type
     *
     * @param filename <tt>hit</tt>, <tt>moni</tt>, <tt>sn</tt>, <tt>tcal</tt>
     * @param index index into FileHandler array
     */
    DataStreamType(String filename, int index)
    {
        this.filename = filename;
        this.index = index;
    }

    /**
     * File name for secondary streams
     *
     * @return file name
     */
    String filename()
    {
        return filename;
    }

    /**
     * Index into FileHandler array
     *
     * @return index
     */
    int index()
    {
        return index;
    }

    /**
     * Stream name
     */
    public String toString()
    {
        return filename;
    }
}

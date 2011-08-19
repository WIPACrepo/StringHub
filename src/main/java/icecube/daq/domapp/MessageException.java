package icecube.daq.domapp;

public class MessageException extends Exception 
{

    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final int returnedType, returnedSubType;
    private final int status;

    public MessageException(MessageType type, Exception e) 
    {
        super(e);
        this.type = type;
        returnedType = 0;
        returnedSubType = 0;
        status = 0;
    }

    public MessageException(MessageType type, int rt, int rs, int status)
    {
        this.type = type;
        returnedType = rt;
        returnedSubType = rs;
        this.status = status;
    }

    public String toString()
    {
        if (getCause() != null) {
            return "DOM Message exception: sent type = " + type.toString() +
                " wrapped exception is " + getCause().toString();
        } else {
            return "DOM Message exception: sent type = " + 
                type.toString() + ", returned type = " + returnedType +
                ", returned subtype = " + returnedSubType +
                ", status = " + Integer.toBinaryString(status) + "b";
        }

}

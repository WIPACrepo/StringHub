package icecube.daq.domapp;

public class MessageException extends Exception {

	private static final long serialVersionUID = 1L;
	
	MessageType type;
	int status;
	
	public MessageException(Exception e) {
		super(e);
        type = null;
        status = 0;
	}
	
	public MessageException(MessageType type, int status) {
		this.type = type;
		this.status = status;
	}
	
	public String toString()
	{
	    if (type != null)
	        return type.toString() + " - status: " + Integer.toHexString(status);
	    // Ok - it's a wrapped exception so just spit out its error message
	    return getCause().toString();
	}
	
}

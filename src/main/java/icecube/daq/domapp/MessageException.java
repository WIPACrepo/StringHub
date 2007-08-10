package icecube.daq.domapp;

public class MessageException extends Exception {

	private static final long serialVersionUID = 1L;
	
	MessageType type;
	int status;
	
	public MessageException(Exception e) {
		super(e);
	}
	
	public MessageException(MessageType type, int status) {
		this.type = type;
		this.status = status;
	}
	
	public String toString()
	{
	    return type.toString() + " - status: " + Integer.toHexString(status);
	}
	
}

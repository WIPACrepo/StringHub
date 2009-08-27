package icecube.daq.dor;

public class GPSException extends Exception {

	private static final long serialVersionUID = 1L;
	protected String procfile;
	private Exception wrappedException;

	public GPSException(String procfile) {
		this.procfile = procfile;
		wrappedException = null;
	}

	public GPSException(String procfile, Exception wrapped) {
		this.procfile = procfile;
		this.wrappedException = wrapped;
	}

	public String toString() {
		String txt = "GPS exception on procfile " + procfile;
		if (wrappedException != null)
			return  txt + " wrapped exception is " + wrappedException;
		else
			return txt;
	}

}

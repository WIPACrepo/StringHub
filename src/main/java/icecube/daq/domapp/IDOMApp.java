package icecube.daq.domapp;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface IDOMApp {

	/**
	 * Begin a run with the DOM flasherboard activated.
	 * @param brightness set the LED brightness - one setting for all 12 LED
	 * @param width set the LED width.  Valid range is [10..127]
	 * @param delay set the delay between the flasher fire and ATWD capture.
	 * @param mask set the LED bitmask.  This directs which of the 12 LEDs will fire.
	 * @param rate set the LED flashing rate.  Note that the actual rate may be rounded
	 * by the HAL.
	 */
	public abstract void beginFlasherRun(short brightness, short width, short delay, 
			short mask, short rate)
	throws MessageException;
	
	/**
	 * Begin data collection on DOM.
	 * @throws MessageException
	 */
	public abstract void beginRun() throws MessageException;

	/**
	 * Collect and compute pedestal information on the DOM.
	 * @param nAtwd0 number of pedestal waveforms to collect for atwd chip 0.
	 * @param nAtwd1 number of pedestal waveforms to collect for atwd chip 0.
	 * @param nFadc number of pedestal waveforms to collect for the fADC.
	 */
	public abstract void collectPedestals(int nAtwd0, int nAtwd1, int nFadc) throws MessageException;
	/**
	 * Disable the PMT HV.  This action should not change the power
	 * state of the HV digital interface PCB.  It should remember
	 * its state across this function call.
	 * @throws MessageException
	 */
	public abstract void disableHV() throws MessageException;

	/**
	 * Disable the supernova scaler readout.
	 * @throws MessageException
	 */
	public abstract void disableSupernova() throws MessageException;

	/**
	 * Enable the PMT HV.  This should not change the power state of the 
	 * HV digital interface PCB.
	 * @throws MessageException
	 */
	public abstract void enableHV() throws MessageException;

	/**
	 * Enable data collection from the supernova system.
	 * @throws MessageException
	 */
	public abstract void enableSupernova(int deadtime, boolean speDisc)
			throws MessageException;

	/**
	 * Terminate data collection on DOM.  This method will dispatch
	 * the appropriate end-of-run message to the DOM depending on
	 * whether the run was begun as a normal run or a flasher run.
	 * @throws MessageException
	 */
	public abstract void endRun() throws MessageException;

	/**
	 * Non-performance-optimized method to obtain data from
	 * the DOMApp.  It simply sends off a GET_DATA message
	 * and returns a ByteBuffer with the DOM data newly created
	 * from the heap. 
	 * @return ByteBuffer containing DOM waveform data (if any)
	 * @throws MessageException
	 */
	public abstract ByteBuffer getData() throws MessageException;

	/**
	 * Query the DOMApp for the DOM mainboard ID (12-char hex string)
	 * @return
	 * @throws MessageException
	 */
	public abstract String getMainboardID() throws MessageException;

	/**
	 * Get whatever monitoring messages are present in the
	 * monitoring buffer.
	 * @return ByteBuffer of monitor messages.
	 * @throws MessageException
	 */
	public abstract ByteBuffer getMoni() throws MessageException;

	/**
	 * Returns the calibration pulser rate setting.
	 * @return the rate in Hz
	 * @throws MessageException
	 */
	public abstract short getPulserRate() throws MessageException;

	/**
	 * Get the DOMApp release tag identifier.
	 * @return ASCII string that is of form VXX-YY-ZZ
	 * for 'official' releases.
	 * @throws MessageException
	 */
	public abstract String getRelease() throws MessageException;

	/**
	 * Returns the (rate meter) scaler deadtime.
	 * @return the artificial deadtime in nanoseconds.
	 * @throws MessageException
	 */
	public abstract int getScalerDeadtime() throws MessageException;

	/**
	 * Retrieve the contents of the DOM supernova scaler buffer.
	 * @return the supernova scaler buffer.
	 * @throws MessageException
	 */
	public abstract ByteBuffer getSupernova() throws MessageException;

	/** 
	 * Turn off the electronic pulser.  This causes
	 * the DOM to emit beacon hits instead.
	 * @throws MessageException
	 */
	public abstract void pulserOff() throws MessageException;

	/**
	 * Turn on the DOM on-board electronic pulser.
	 * This ceases beacon hit production.
	 * @throws MessageException
	 */
	public abstract void pulserOn() throws MessageException;

	/**
	 * Get the HV readback / programmed values
	 * @return 2-element array [0] = ADC value (readback) [1] = DAC value (set)
	 * @throws MessageException
	 */
	public abstract short[] queryHV() throws MessageException;

	/**
	 * Set the LC cable lengths.
	 * @param up 4-element array of shorts holding the up link delays.
	 * @param dn 4-element array of shorts holding the down link delays.
	 * @throws MessageException
	 */
	public abstract void setCableLengths(short[] up, short[] dn)
			throws MessageException;

	/**
	 * Prepare the DOM to emit delta compression data.
	 * @throws MessageException
	 */
	public abstract void setDeltaCompressionFormat() throws MessageException;

	/**
	 * Sets the engineering hit data format.
	 * @param fmt the engineering record format
	 * @throws MessageException
	 */
	public abstract void setEngineeringFormat(EngineeringRecordFormat fmt)
			throws MessageException;

	/**
	 * Set the PMT HV programming DAC.
	 * @param dac HV volts * 2, must be in range [0..4095]
	 * @throws MessageException
	 */
	public abstract void setHV(short dac) throws MessageException;

	/**
	 * Sets the LC (Rx) mode.  This determines whether the DOM
	 * will require presence of neighboring DOM LC signals.
	 * @param mode the LC mode
	 * @throws MessageException
	 */
	public abstract void setLCMode(LocalCoincidenceConfiguration.RxMode mode)
			throws MessageException;

	/**
	 * Set the source of the LC triggers (SPE or MPE).
	 * @param src LC source specifier
	 * @throws MessageException
	 */
	public abstract void setLCSource(LocalCoincidenceConfiguration.Source src)
			throws MessageException;
	
	/**
	 * Set the LC span.
	 * @param span spanning argument in range [1..4]
	 * @throws MessageException
	 */
	public abstract void setLCSpan(byte span) throws MessageException;
	
	/**
	 * Set the LC Tx mode.  This determines whether the DOM
	 * will emit the LC signals on trigger.
	 * @param mode the LC Tx mode
	 * @throws MessageException
	 */
	public abstract void setLCTx(LocalCoincidenceConfiguration.TxMode mode)
			throws MessageException;

	/**
	 * Select among soft / hard / flabby local coincidence behavior.
	 * @param type the type setting
	 * @throws MessageException
	 */
	public abstract void setLCType(LocalCoincidenceConfiguration.Type type)
			throws MessageException;

	/**
	 * Sets the LC pre / post trigger windows.
	 * @param pre pre-trigger window in nanoseconds
	 * @param post post-trigger window in nanoseconds
	 * @throws MessageException
	 */
	public abstract void setLCWindow(int pre, int post) throws MessageException;

	/**
	 * Specify the rate at which the DOM will produce monitoring records
	 * @param hw
	 * @param config
	 * @throws MessageException
	 */
	public abstract void setMoniIntervals(int hw, int config)
			throws MessageException;

	/**
	 * Select the input for the analog multiplexer.
	 * @param mode muxer setting
	 * @throws MessageException
	 */
	public abstract void setMux(MuxState mode) throws MessageException;

	/**
	 * Set the calibration pulser rate.
	 * @param rate the rate in Hz
	 * @throws MessageException
	 */
	public abstract void setPulserRate(short rate) throws MessageException;

	/**
	 * Set the rate monitor scaler deadtime.
	 * @param deadtime the deadtime in nanoseconds
	 * @throws MessageException
	 */
	public abstract void setScalerDeadtime(int deadtime)
			throws MessageException;

	/**
	 * Set the DOM triggering mode
	 * @param mode trigger mode enumeration
	 * @throws MessageException
	 */
	public abstract void setTriggerMode(TriggerMode mode)
			throws MessageException;

	/**
	 * Put the DOM into DOMApp.  On entry to this call the DOM
	 * may be either in iceboot or domapp.  It makes a determination
	 * of whether or not it is already in domapp by sending a
	 * GET_DOM_ID message (safe for configboot, too).  If the DOM
	 * responds with the expected response then that indicates in
	 * domapp state already.  If in iceboot the response will be
	 * an error message.  If that pattern is seen then the message is
	 * consumed and the sequence initiated to get the DOM from iceboot
	 * into domapp.  Note that this process can take a long time.  At
	 * present the code just waits 5.0 sec after sending the commands
	 * which under normal circumstances should be plenty of time to
	 * get the DOM into domapp. At the end of this call the DOM should 
	 * have properly loaded the domapp.sbi and exec'd the dom cpu app.
	 * @throws IOException
	 * @throws InterruptedException
	 * @return true if the dom was in iceboot and needed a phase
	 * transition, false if the dom was already in domapp.
	 */
	public abstract boolean transitionToDOMApp() throws IOException,
			InterruptedException;

	/**
	 * Write a single DAC.
	 * Note that DACs 0 through 7 are 12-bit and 8
	 * through 15 are 10-bit.  
	 * @param dac the DAC channel to write to [0..15]
	 * @param val value to program to DAC
	 * @throws MessageException
	 */
	public abstract void writeDAC(byte dac, short val) throws MessageException;

}
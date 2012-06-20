package icecube.daq.domapp;

/**
 * DOM pulser mode enumeration.  The pulser can either be off in which case the DOM generates
 * beacon hits or on in which case the electronic pulser on the analog front end is activated.
 * @author krokodil
 *
 */
public enum PulserMode {
	BEACON, PULSER
};
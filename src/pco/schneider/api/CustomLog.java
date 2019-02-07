package pco.schneider.api;

import java.util.ArrayList;
import java.util.List;

public class CustomLog {
	
	private static CustomLog classInstance;

	public static final int ERROR_STATUS = 2;
	
	public static final int WARNING_STATUS = 1;
	
	public static final int OK_STATUS = 0;

	private transient int status = 0;

	private transient List<String> traces;

	// -------------------------------------------------------------------------------------------------------------
	/**
	 * 
	 * @return The unique instance of UserLog.
	 */
	public static CustomLog getInstance() {
		return classInstance == null ? classInstance = new CustomLog() : getClassInstance();
	}

	// -------------------------------------------------------------------------------------------------------------
	public CustomLog() {
		traces = new ArrayList<String>();
	}

	public void clear() {
		traces = new ArrayList<String>();
	}

	// -------------------------------------------------------------------------------------------------------------
	/**
	 * 
	 * @param msg
	 */
	public int warn(final String msg) {
		traces.add(msg);
//    traces.add("WARN: " + msg);

		if (status == OK_STATUS) {
			status = WARNING_STATUS;
		}

		return WARNING_STATUS;
	}

	// -------------------------------------------------------------------------------------------------------------
	/**
	 * 
	 * @param msg
	 */
	public void error(final String msg) {
		traces.add("ERROR: " + msg);

		status = ERROR_STATUS;
	}

	// -------------------------------------------------------------------------------------------------------------
	/**
	 * 
	 * @param msg
	 */
	public void info(final String msg) {
		traces.add("INFO: " + msg);
	}

	// -------------------------------------------------------------------------------------------------------------
	/**
	 * 
	 * @return On of values {ERROR_STATUS,WARNING_STATUS,OK_STATUS}.
	 */
	public int getStatus() {
		return status;
	}

	// -------------------------------------------------------------------------------------------------------------
	/**
	 * @return the classIsnatnce
	 */
	private static CustomLog getClassInstance() {
		return classInstance;
	}

	// -------------------------------------------------------------------------------------------------------------
	/**
	 * @return the traces
	 */
	public List<String> getTraces() {
		return traces;
	}
	
}

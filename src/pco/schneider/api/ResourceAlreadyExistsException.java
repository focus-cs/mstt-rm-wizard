package pco.schneider.api;

public class ResourceAlreadyExistsException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ResourceAlreadyExistsException(String errorMessage) {
		super(errorMessage);
	}

}

package service;

/** A business-rule or persistence failure whose message is safe to show to the user. */
public class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ServiceException(String message) {
        super(message);
    }
}

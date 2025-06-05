package io.logdash.sdk.exception;

import java.io.Serial;

/**
 * Runtime exception thrown when Logdash SDK operations fail.
 *
 * <p>This exception indicates various error conditions such as:
 *
 * <ul>
 *   <li>Invalid configuration parameters
 *   <li>Network connectivity issues
 *   <li>API authentication failures
 *   <li>Invalid input parameters
 * </ul>
 */
public class LogdashException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new LogdashException with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public LogdashException(String message) {
        super(message);
    }

    /**
     * Constructs a new LogdashException with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause   the underlying cause of this exception
     */
    public LogdashException(String message, Throwable cause) {
        super(message, cause);
    }
}

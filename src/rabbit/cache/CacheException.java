package rabbit.cache;

/** An exception thrown when a cache operation failed.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class CacheException extends Exception {
    /** The serial version id */
    public static final long serialVersionUID = 1;

    /**
     * @param message the message string
     * @param cause the exception that really caused the problem
     */
    public CacheException (String message, Throwable cause) {
	super (message, cause);
    }
}

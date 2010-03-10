package rabbit.cache;

/** An exception thrown when a cache operation failed.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class CacheException extends Exception {
    public static final long serialVersionUID = 1;

    public CacheException (String message, Throwable cause) {
	super (message, cause);
    }
}

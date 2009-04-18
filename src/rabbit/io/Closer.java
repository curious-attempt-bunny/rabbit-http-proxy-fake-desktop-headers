package rabbit.io;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A helper class that can close resources without throwing exceptions.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class Closer {

    public static void close (Closeable c, Logger logger) {
	if (c == null)
	    return;
	try {
	    c.close ();
	} catch (IOException e) {
	    logger.log (Level.WARNING, 
			"Failed to close connection: " + c, 
			e);
	}
    }
}

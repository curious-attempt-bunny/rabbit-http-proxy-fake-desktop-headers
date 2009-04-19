package rabbit.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** An object that can read and write objects to file.
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface FileHandler<T> {
    /** Read a T from the given stream */
    T read (InputStream is) throws IOException;

    /** Write a T to the given stream */
    void write (OutputStream os, T t) throws IOException;
}    

package rabbit.io;

import java.nio.ByteBuffer;

/** A ByteBuffer handler
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface BufferHandler {
    /** Get a byte buffer of reasonable size, the buffer will have been cleared. */
    ByteBuffer getBuffer ();

    /** Return a buffer. */
    void putBuffer (ByteBuffer buffer);

    /** Get a larger buffer with the same contents as buffer, this
     *  will also return buffer to the pool.
     * @param buffer an existing buffer, the contents will be copied into 
     *        the new larger buffer. May be null.
     */
    ByteBuffer growBuffer (ByteBuffer buffer);
}

package rabbit.io;

import java.nio.ByteBuffer;

/** A ByteBuffer handler
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface BufferHandler {
    /** Get a byte buffer of reasonable size, the buffer will have been cleared. */
    ByteBuffer getBuffer ();

    /** return a buffer. */
    void putBuffer (ByteBuffer buffer);

    /** Get a larger buffer with the same contents as buffer, this
     *  will also return buffer to the pool.
     */
    ByteBuffer growBuffer (ByteBuffer buffer);
}

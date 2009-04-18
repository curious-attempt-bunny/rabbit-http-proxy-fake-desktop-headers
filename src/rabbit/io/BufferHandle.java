package rabbit.io;

import java.nio.ByteBuffer;

/** A handle to a ByteBuffer 
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface BufferHandle {
    /** Check if this handle is empty, that is if no buffer exists 
     *or the buffer is empty. 
     */
    boolean isEmpty ();

    /** Get a byte buffer of reasonable size, the buffer will have been cleared. */
    ByteBuffer getBuffer ();

    /** Grow the buffer.*/
    void growBuffer ();

    /** release a buffer if possible. */
    void possiblyFlush ();
}

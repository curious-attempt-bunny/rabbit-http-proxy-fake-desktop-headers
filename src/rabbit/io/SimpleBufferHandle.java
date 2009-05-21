package rabbit.io;

import java.nio.ByteBuffer;

/** A handle to a ByteBuffer.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SimpleBufferHandle implements BufferHandle {
    private ByteBuffer buffer;

    public SimpleBufferHandle (ByteBuffer buffer) {
	this.buffer = buffer;
    }

    public boolean isEmpty () {
	return !buffer.hasRemaining ();
    }
    
    public ByteBuffer getBuffer () {
	return buffer;
    }

    public ByteBuffer getLargeBuffer () {
	throw new RuntimeException ("Not implemented");
    }

    public void possiblyFlush () {
	if (!buffer.hasRemaining ())
	    buffer = null;
    }
}

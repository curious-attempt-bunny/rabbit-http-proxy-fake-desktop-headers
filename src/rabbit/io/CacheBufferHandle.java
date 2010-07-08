package rabbit.io;

import java.nio.ByteBuffer;

/** A handle to a ByteBuffer that uses a buffer handler
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class CacheBufferHandle implements BufferHandle {
    private final BufferHandler bh;
    private ByteBuffer buffer;

    public CacheBufferHandle (BufferHandler bh) {
	this.bh = bh;
    }

    public boolean isEmpty () {
	return buffer == null || !buffer.hasRemaining ();
    }
    
    public ByteBuffer getBuffer () {
	if (buffer == null)
	    buffer = bh.getBuffer ();
	return buffer;
    }
    
    public ByteBuffer getLargeBuffer () {
	buffer = bh.growBuffer (buffer);
	return buffer;
    }

    public boolean isLarge (ByteBuffer buffer) {
	return bh.isLarge (buffer);
    }

    public void possiblyFlush () {
	if (buffer == null)
	    return;
	if (!buffer.hasRemaining ()) {
	    bh.putBuffer (buffer);
	    buffer = null;
	}
    }

    @Override public String toString () {
	return getClass ().getName () + "[buffer: " + buffer + 
	    ", bh: " + bh + "}";
    }
}

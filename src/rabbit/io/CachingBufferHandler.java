package rabbit.io;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** A ByteBuffer handler that keeps re uses returned buffers.
 *
 *  This class uses no synchronization.
 *
 *  This class only allocates direct buffers.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class CachingBufferHandler implements BufferHandler {
    private final Queue<BufferHolder> cache =
	new ConcurrentLinkedQueue<BufferHolder> ();
    private final Queue<BufferHolder> largeCache =
	new ConcurrentLinkedQueue<BufferHolder> ();
    private int count = 0;

    private static final int SMALL_BUFFER_SIZE = 4096;
    private static final int LARGE_BUFFER_SIZE = 128 * 1024;
    
    private ByteBuffer getBuffer (Queue<BufferHolder> bufs, int size) {
	count++;
	BufferHolder r = bufs.poll ();
	ByteBuffer b;
	if (r != null) {
	    b = r.getBuffer ();
	} else {
	    b = ByteBuffer.allocateDirect (size);
	}
	b.clear ();
	return b;
    }

    public ByteBuffer getBuffer () {
	return getBuffer (cache, SMALL_BUFFER_SIZE);
    }
    
    private void addCache (Queue<BufferHolder> bufs, BufferHolder bh) {
	bufs.add (bh);
    }

    public void putBuffer (ByteBuffer buffer) {
	if (buffer == null) 
	    throw new IllegalArgumentException ("null buffer not allowed");
	count--;	
	BufferHolder bh = new BufferHolder (buffer);
	if (buffer.capacity () == SMALL_BUFFER_SIZE)
	    addCache (cache, bh);
	else 
	    addCache (largeCache, bh);
    }
    
    public ByteBuffer growBuffer (ByteBuffer buffer) {
	ByteBuffer lb = getBuffer (largeCache, LARGE_BUFFER_SIZE);
	if (buffer != null) {
	    int position = buffer.position ();
	    lb.put (buffer);
	    lb.position (position);
	    putBuffer (buffer);
	}
	return lb;
    }

    public boolean isLarge (ByteBuffer buffer) {
	return buffer.capacity () > SMALL_BUFFER_SIZE;
    }

    private static final class BufferHolder {
	private final ByteBuffer buffer;
	
	public BufferHolder (ByteBuffer buffer) {
	    this.buffer = buffer;
	}

	// Two holders are equal if they hold the same buffer
	@Override public boolean equals (Object o) {
	    if (o == null)
		return false;
	    if (o == this)
		return true;

	    // ByteBuffer.equals depends on content, not what I want.
	    if (o instanceof BufferHolder)
		return ((BufferHolder)o).buffer == buffer;
	    return false;
	}

	@Override public int hashCode () {
	    // ByteBuffer.hashCode depends on its contents.
	    return System.identityHashCode (buffer);
	}

	public ByteBuffer getBuffer () {
	    return buffer;
	}
    }
}

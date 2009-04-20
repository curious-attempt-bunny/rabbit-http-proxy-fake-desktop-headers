package rabbit.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import rabbit.cache.Cache;
import rabbit.http.Header;
import rabbit.http.HttpHeader;
import rabbit.http.MultipartHeader;
import rabbit.httpio.BlockListener;
import rabbit.io.BufferHandler;
import rabbit.io.Range;
import rabbit.nio.NioHandler;

/** A resource that gets ranges from the cache.
 *  This resource will read data from disk so it may block.
 * 
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class RandomCacheResourceSource extends CacheResourceSource {
    private String contentType;
    private List<Range> ranges;
    private int currentRange = 0;
    private String separator = "THIS_STRING_SEPARATES";
    private boolean startBlock = true;
    private long currentIndex = 0;
    private long totalSize;

    private enum State { SEND_HEADER, SEND_DATA };
    private State state = State.SEND_HEADER;

    public RandomCacheResourceSource (Cache<HttpHeader, HttpHeader> cache,
				      RequestHandler rh,
				      NioHandler tr, BufferHandler bufHandler,
				      List<Range> ranges, long totalSize) 
	throws IOException {
	super (cache, rh.getEntry (), tr, bufHandler);
	HttpHeader oldresp = rh.getDataHook ();
	contentType = oldresp.getHeader ("Content-Type");
	this.ranges = ranges;
	this.totalSize = totalSize;
    }    

    /** FileChannels can only be partially used so go with blocks.
     * @return false
     */
    @Override public boolean supportsTransfer () {
	return false;
    }

    private boolean getNextSingleBufferBlock (ByteBuffer buffer) 
	throws IOException {
	if (currentRange > 0)
	    return false;
	Range r = ranges.get (currentRange);
	updateBufferAndPosition (buffer, r);
	return true;
    }

    /** Fill the buffer with data for the current range. 
     *  If the range is fully handled then currentRange will be incremented.
     */
    private void updateBufferAndPosition (ByteBuffer buffer, Range r) 
	throws IOException {
	if (startBlock) {
	    fc.position (r.getStart ());
	    currentIndex = r.getStart ();
	    startBlock = false;
	}
	fillBufferWithData (r, buffer);
	// if something fishy happen we abort...
	// inclusive, so we expect 1 more than end.
	if (r.size () == 0 || buffer.position () == 0 || 
	    currentIndex > r.getEnd ())
	    currentRange++;
    }

    private void fillBufferWithData (Range r, ByteBuffer buffer) 
	throws IOException {
	int maxBytesThisRead = 
	    (int)Math.min (r.size (), buffer.capacity ());
	if (maxBytesThisRead < buffer.capacity ())
	    buffer.limit (maxBytesThisRead);
	int read = fc.read (buffer);
	currentIndex += read;
    }

    private boolean getNextMultipleBufferBlock (ByteBuffer buffer) 
	throws IOException {
	if (currentRange > ranges.size ())
	    return false;
	if (currentRange == ranges.size ()) {
	    // CRLF should be optional according to BNF, but add it 
	    // since the rfc say it should be there.
	    buffer.clear ();
	    buffer.put ((Header.CRLF + "--" + separator + 
			 "--" + Header.CRLF).getBytes ());
	    currentRange++;
	    return true;
	} 
	
	int r1 = currentRange;
	Range r = ranges.get (currentRange);
	if (state == State.SEND_HEADER) {
	    buffer.clear ();
	    writeHeader (buffer);
	    startBlock = true;
	    state = State.SEND_DATA;
	} else  if (state == State.SEND_DATA) {
	    updateBufferAndPosition (buffer, r);
	    if (currentRange != r1)
		state = State.SEND_HEADER;
	}
	return true;
    }

    /** Write the current MultipartHeader to the buffer. 
     */
    private void writeHeader (ByteBuffer buffer) {
	MultipartHeader h = 
	    new MultipartHeader (Header.CRLF + "--" + separator);
	Range r = ranges.get (currentRange);
	if (contentType != null)
	    h.setHeader ("Content-Type", contentType);
	h.setHeader ("Content-Range", "bytes " + r.getStart () + "-" + 
		     r.getEnd () + "/" + totalSize);
	buffer.put (h.toString ().getBytes ());
    }
    
    private boolean getNextBuffer (ByteBuffer buffer) 
	throws IOException {
	if (ranges.size () > 1) {
	    return getNextMultipleBufferBlock (buffer);
	}
	return getNextSingleBufferBlock (buffer);
    }
    
    @Override public void addBlockListener (BlockListener listener) {
	try {
	    ByteBuffer buffer = bufHandle.getBuffer ();	 
	    if (getNextBuffer (buffer)) {
		buffer.flip ();
		listener.bufferRead (bufHandle);
	    } else {
		bufHandle.possiblyFlush ();
		listener.finishedRead ();
	    }
	} catch (IOException e) {
	    listener.failed (e);
	}
    }
}

package rabbit.httpio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.StringTokenizer;
import rabbit.io.BufferHandle;
import rabbit.io.SimpleBufferHandle;

/** The chunk handler gets raw data buffers and passes the de-chunked content
 *  to the listener.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ChunkHandler {
    private final ChunkDataFeeder feeder;
    private final boolean strictHttp;
    private int currentChunkSize = -1;
    private int readFromChunk = 0;
    private boolean readTrailingCRLF = false;
    private boolean readExtension = false;
    private BlockListener listener;
    private long totalRead = 0;

    /** Create a new ChunkHandler that will get data from the given feeder.
     * @param feeder the raw data provider
     * @param strictHttp if true then parse http strict, that is use \r\n
     *        for line breaks.
     */
    public ChunkHandler (ChunkDataFeeder feeder, boolean strictHttp) {
	this.feeder = feeder;
	this.strictHttp = strictHttp;
    }

    /** Set the chunk block listener.
     * @param listener the listener for the chunk data
     */
    public void setBlockListener (BlockListener listener) {
	this.listener = listener;
    }

    /** Get the total number of chunk block data bytes read
     * @return the number of chunk data bytes read
     */
    public long getTotalRead () {
	return totalRead;
    }

    private boolean needChunkSize () {
	return currentChunkSize == -1;
    }

    /** Try to parse and handle the new data
     * @param bufHandle the data to parse
     */
    public void handleData (BufferHandle bufHandle) {
	try {
	    ByteBuffer buffer = bufHandle.getBuffer ();
	    if (needChunkSize ()) {
		buffer.mark ();
		if (!readTrailingCRLF && totalRead > 0) {
		    if (buffer.remaining () < 2) {
			feeder.readMore ();
			return;
		    }
		    readOffCRLF (buffer);
		    buffer.mark ();
		}
		LineReader lr = new LineReader (strictHttp);
		lr.readLine (buffer, new ChunkSizeHandler ());
		if (currentChunkSize == 0) {
		    readFooter (bufHandle);
		} else if (currentChunkSize > 0) {
		    readFromChunk = 0;
		    handleChunkData (bufHandle);
		} else {
		    buffer.reset ();
		    if (buffer.position () > 0) {
			feeder.readMore ();
		    } else {
			if (checkChunkSizeAndExtension (buffer)) {
			    // rest of buffer is a huge extension that we
			    // do not recognize, so we ignore it...
			    feeder.readMore ();
			} else {
			    String err = "failed to read chunk size";
			    listener.failed (new IOException (err));
			}
		    }
		}
	    } else if (readExtension) {
		tryToReadExtension (bufHandle);
	    } else {
		if (currentChunkSize == 0)
		    readFooter (bufHandle);
		else
		    handleChunkData (bufHandle);
	    }
	} catch (BadChunkException e) {
	    listener.failed (e);
	}
    }

    private void tryToReadExtension (BufferHandle bufHandle) {
	LineReader lr = new LineReader (strictHttp);
	lr.readLine (bufHandle.getBuffer (), new ExtensionHandler ());
	if (readExtension) {
	    feeder.readMore ();
	} else {
	    if (currentChunkSize == 0) {
		readFooter (bufHandle);
	    } else if (currentChunkSize > 0) {
		readFromChunk = 0;
		handleChunkData (bufHandle);
	    }
	}
    }

    private boolean checkChunkSizeAndExtension (ByteBuffer buffer) {
	buffer.mark ();
	StringBuilder sb = new StringBuilder ();
	while (buffer.remaining () > 0) {
	    byte b = buffer.get ();
	    if (!(b >= '0' && b <= '9' ||
		  b >= 'a' && b <= 'f' ||
		  b >= 'A' && b <= 'F' ||
		  b == ';')) {
		buffer.reset ();
		return false;
	    }
	    if (b == ';') {
		// ok, extension follows.
		currentChunkSize = Integer.parseInt (sb.toString (), 16);
		readExtension = true;
		buffer.position (buffer.limit ());
		return true;
	    }
	    sb.append ((char)b);
	}
	// ok, if we get here it may be a valid chunk size,
	// but it will be very large... ignore for now.
	return false;
    }

    private void handleChunkData (BufferHandle bufHandle) {
	ByteBuffer buffer = bufHandle.getBuffer ();
	int remaining = buffer.remaining ();
	int leftInChunk = currentChunkSize - readFromChunk;
	int thisChunk = Math.min (remaining, leftInChunk);
	if (thisChunk == 0) {
	    feeder.readMore ();
	    return;
	}
	readFromChunk += thisChunk;
	totalRead += thisChunk;
	if (thisChunk < remaining) {
	    ByteBuffer copy = buffer.duplicate ();
	    int nextPos = buffer.position () + thisChunk;
	    copy.limit (nextPos);
	    buffer.position (nextPos);
	    if (readFromChunk == currentChunkSize) {
		currentChunkSize = -1;
		readTrailingCRLF = false;
	    }
	    listener.bufferRead (new SimpleBufferHandle (copy));
	} else {
	    // all the rest of the current buffer
	    if (readFromChunk == currentChunkSize) {
		currentChunkSize = -1;
		readTrailingCRLF = false;
	    }
	    listener.bufferRead (bufHandle);
	}
    }

    private void readOffCRLF (ByteBuffer buffer) throws BadChunkException {
	byte b1 = buffer.get ();
	byte b2 = buffer.get ();
	if (!(b1 == '\r' && b2 == '\n'))
	    throw new BadChunkException ("F to read CRLF: " +
					 (int)b1 + ", " + (int)b2);
	readTrailingCRLF = true;
    }

    private void readFooter (BufferHandle bufHandle) {
	LineReader lr = new LineReader (strictHttp);
	EmptyLineHandler elh;
	ByteBuffer buffer = bufHandle.getBuffer ();
	do {
	    buffer.mark ();
	    elh = new EmptyLineHandler ();
	    lr.readLine (buffer, elh);
	    if (!elh.lineRead ()) {
		feeder.readMore ();
		return;
	    }
	} while (!elh.ok ());
	listener.finishedRead ();
    }

    private static class EmptyLineHandler implements LineListener {
	private boolean ok = false;
	private boolean lineRead = false;
	public void lineRead (String line) {
	    lineRead = true;
	    ok = "".equals (line);
	}
	public boolean ok () {
	    return ok;
	}
	public boolean lineRead () {
	    return lineRead;
	}
    }

    private class BadChunkException extends RuntimeException {
	public static final long serialVersionUID  = 1L;
	public BadChunkException (String msg) {
	    super (msg);
	}

	public BadChunkException (String msg, Throwable cause) {
	    super (msg, cause);
	}
    }

    private class ChunkSizeHandler implements LineListener {
	public void lineRead (String line) {
	    StringTokenizer st = new StringTokenizer (line, "\t \n\r(;");
	    if (st.hasMoreTokens ()) {
		String hex = st.nextToken ();
		try {
		    currentChunkSize = Integer.parseInt (hex, 16);
		} catch (NumberFormatException e) {
		    String err = "Chunk size is not a hex number: '" +
			line + "', '" + hex + "'.";
		    throw new BadChunkException (err, e);
		}
	    } else {
		throw new BadChunkException ("Chunk size is not available.");
	    }
	}
    }

    private class ExtensionHandler implements LineListener {
	public void lineRead (String line) {
	    readExtension = false;
	}
    }
}

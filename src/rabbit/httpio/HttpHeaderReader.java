package rabbit.httpio;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import rabbit.http.Header;
import rabbit.http.HttpHeader;
import rabbit.io.BufferHandle;
import rabbit.nio.NioHandler;
import rabbit.nio.ReadHandler;
import rabbit.util.TrafficLogger;

/** A handler that reads http headers
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HttpHeaderReader extends BaseSocketHandler 
    implements LineListener, ReadHandler {
    
    private HttpHeader header;
    private Header head = null;    
    private boolean append = false;
    private final boolean request;
    private final boolean strictHttp;
    private final HttpHeaderListener reader;
    private boolean headerRead = false;

    // State variables.
    private boolean keepalive = true;
    private boolean ischunked = false;
    private long dataSize = -1;   // -1 for unknown.
    private int startParseAt = 0;

    private final TrafficLogger tl;
    private LineReader lr;

    private static final ByteBuffer HTTP_IDENTIFIER = 
    ByteBuffer.wrap (new byte[]{(byte)'H', (byte)'T', (byte)'T', 
				(byte)'P', (byte)'/'});
    
    private static final ByteBuffer EXTRA_LAST_CHUNK = 
    ByteBuffer.wrap (new byte[]{(byte)'0', (byte)'\r', (byte)'\n', 
				(byte)'\r', (byte)'\n'});
    

    /** 
     * @param request true if a request is read, false if a response is read.
     *                Servers may respond without header (HTTP/0.9) so try to 
     *                handle that.
     */ 
    public HttpHeaderReader (SocketChannel channel, BufferHandle bh, 
			     NioHandler nioHandler, TrafficLogger tl, 
			     boolean request, boolean strictHttp, 
			     HttpHeaderListener reader) {
	super (channel, bh, nioHandler);
	this.tl = tl;
	this.request = request;
	this.strictHttp = strictHttp;
	this.reader = reader;
    }

    public void readRequest () throws IOException {
	if (!getBufferHandle ().isEmpty ()) {
	    ByteBuffer buffer = getBuffer ();
	    startParseAt = buffer.position ();
	    parseBuffer (buffer);
	} else {
	    releaseBuffer ();
	    waitForRead (this);
	}
    }

    @Override public String getDescription () {
	return "HttpHeaderReader: channel: " + getChannel () + 
	    ", current header lines: " + 
	    (header == null ? 0 : header.size ());
    }

    @Override public void closed () {
	releaseBuffer ();
	reader.closed ();
    }

    @Override public void timeout () {
	// If buffer exists it only holds a partial http header.
	// We relase the buffer and discard that partial header.
	releaseBuffer ();
	reader.timeout ();
    }
    
    public void read () {
	Logger logger = getLogger ();
	logger.finest ("HttpHeaderReader reading data");
	try {
	    // read http request
	    // make sure we have room for reading.
	    ByteBuffer buffer = getBuffer ();
	    int pos = buffer.position ();
	    buffer.limit (buffer.capacity ());
	    int read = getChannel ().read (buffer);
	    if (read == -1) {
		buffer.position (buffer.limit ());
		closeDown ();
		reader.closed ();
		return;
	    } 
	    if (read == 0) {
		closeDown ();
		reader.failed (new IOException ("read 0 bytes, shutting " + 
						"down connection"));
		return;
	    }
	    tl.read (read);
	    buffer.position (startParseAt);
	    buffer.limit (read + pos);
	    parseBuffer (buffer);
	} catch (IOException e) {
	    closeDown ();
	    reader.failed (e);
	}
    }
    
    private void parseBuffer (ByteBuffer buffer) throws IOException {
	int startPos = buffer.position ();
	buffer.mark ();
	boolean done = handleBuffer (buffer);
	Logger logger = getLogger ();
	if (logger.isLoggable (Level.FINEST))
	    logger.finest ("HttpHeaderReader.parseBuffer: done " + done);
	if (!done) {
	    int fullPosition = buffer.position ();
	    buffer.reset ();
	    int pos = buffer.position ();
	    if (pos == startPos) {
		if (buffer.remaining () + pos >= buffer.capacity ()) {
		    releaseBuffer ();
		    // ok, we did no progress, abort, client is sending 
		    // too long lines. 
		    // TODO: perhaps grow buffer....
		    throw new RequestLineTooLongException ();
		}
		// set back position so the next read aligns...
		buffer.position (fullPosition);
	    } else {
		// ok, some data handled, make space for more.
		buffer.compact ();
		startParseAt = 0;
	    }
	    waitForRead (this);
	} else {
	    setState ();
	    releaseBuffer ();
	    reader.httpHeaderRead (header, getBufferHandle (), 
				   keepalive, ischunked, dataSize);
	}
    }

    private void setState () {
	dataSize = -1;
	String cl = header.getHeader ("Content-Length");
	if (cl != null) {
	    try {
		dataSize = Long.parseLong (cl);
	    } catch (NumberFormatException e) {
		dataSize = -1;
	    }
	}
	String con = header.getHeader ("Connection");
	// Netscape specific header...
	String pcon = header.getHeader ("Proxy-Connection");
	if (con != null && con.equalsIgnoreCase ("close"))
	    setKeepAlive (false);
	if (keepalive && pcon != null && pcon.equalsIgnoreCase ("close"))
	    setKeepAlive (false);
	
	if (header.isResponse ()) {
	    if (header.getResponseHTTPVersion ().equals ("HTTP/1.1")) {
		String chunked = header.getHeader ("Transfer-Encoding");
		setKeepAlive (true);
		ischunked = false;
		
		if (chunked != null && chunked.equalsIgnoreCase ("chunked")) {
		    /* If we handle chunked data we must read the whole page
		     * before continuing, since the chunk footer must be 
		     * appended to the header (read the RFC)...
		     * 
		     * As of RFC 2616 this is not true anymore...
		     * this means that we throw away footers and it is legal.
		     */
		    ischunked = true;
		    header.removeHeader ("Content-Length");
		    dataSize = -1;
		}
	    } else {
		setKeepAlive (false);
	    }
	    
	    if (!(dataSize > -1 || ischunked))
		setKeepAlive (false);
	} else {
	    String httpVersion = header.getHTTPVersion ();
	    if (httpVersion != null) {
		if (httpVersion.equals ("HTTP/1.1")) {
		    String chunked = header.getHeader ("Transfer-Encoding");
		    if (chunked != null && chunked.equalsIgnoreCase ("chunked")) {
			ischunked = true;
			header.removeHeader ("Content-Length");
			dataSize = -1;
		    }
		} else if (httpVersion.equals ("HTTP/1.0")) {
		    String ka = header.getHeader ("Connection");
		    if (ka == null || !ka.equalsIgnoreCase ("Keep-Alive"))
			setKeepAlive (false);			
		}
	    }
	}
    }

    /** read the data from the buffer and try to build a http header.
     * 
     * @return true if a full header was read, false if more data is needed.
     */
    private boolean handleBuffer (ByteBuffer buffer) throws IOException {
	if (!request && header == null && !verifyResponse (buffer))
	    return true;
	if (lr == null)
	    lr = new LineReader (strictHttp);
	while (!headerRead && buffer.hasRemaining ())
	    lr.readLine (buffer, this);
	return headerRead;
    }

    /** Verify that the response starts with "HTTP/" 
     *  Failure to verify response => treat all of data as content = HTTP/0.9.
     */
    private boolean verifyResponse (ByteBuffer buffer) throws IOException {
	// some broken web servers (apache/2.0.4x) send multiple last-chunks
	if (buffer.remaining () > 4 && matchBuffer (EXTRA_LAST_CHUNK)) {
	    getLogger ().warning ("Found a last-chunk, trying to ignore it.");
	    buffer.position (buffer.position () + EXTRA_LAST_CHUNK.capacity ());
	    return verifyResponse (buffer);
	}

	if (buffer.remaining () > 4 && !matchBuffer (HTTP_IDENTIFIER)) {
	    getLogger ().warning ("http response header with odd start:" + 
				  getBufferStartString (buffer, 5));
	    // Create a http/0.9 response...
	    header = new HttpHeader ();
	    return true;
	}

	return true;
    }

    private boolean matchBuffer (ByteBuffer test) {
	int len = test.remaining ();
	ByteBuffer buffer = getBuffer ();
	if (buffer.remaining () < len)
	    return false;
	int pos = buffer.position ();
	for (int i = 0; i < len; i++)
	    if (buffer.get (pos + i) != test.get (i))
		return false;
	return true;
    }

    private String getBufferStartString (ByteBuffer buffer, int size) {
	try {
	    int pos = buffer.position ();
	    byte[] arr = new byte[size];
	    buffer.get (arr);
	    buffer.position (pos);
	    return new String (arr, "ASCII");
	} catch (UnsupportedEncodingException e) {
	    return "unable to get ASCII: " + e.toString ();
	}
    }    
    
    /** Handle a newly read line. */
    public void lineRead (String line) throws IOException {
	if (line.length () == 0) {
	    headerRead = header != null;
	    return;
	}

	if (header == null) {
	    header = new HttpHeader ();
	    header.setRequestLine (line);
	    headerRead = false;
	    return;
	}

	if (header.isDot9Request ()) {
	    headerRead = true;
	    return;
	}

	char c;
	if (header.size () == 0 &&
	    line.length () > 0 && 
	    ((c = line.charAt (0)) == ' ' || c == '\t')) {
	    header.setReasonPhrase (header.getReasonPhrase () + line);
	    headerRead = false;
	    return;
	} 

	readHeader (line);
	headerRead = false;
    }

    public void readHeader (String msg) throws IOException {
	if (msg == null) 
	    throw (new IOException ("Couldnt read headers, connection must be closed"));
	char c = msg.charAt (0);
	if (c == ' ' || c == '\t' || append) {
	    if (head != null) {
		head.append (msg);
		append = checkQuotes (head.getValue ());
	    } else {
		SocketChannel channel = getChannel ();
		throw (new IOException ("Malformed header from: " + 
					channel.socket ().getInetAddress () +
					", msg: " + msg));
	    }
	    return;
	}
	int i = msg.indexOf (':');	    
	if (i < 0) {
	    switch (msg.charAt (0)) {
	    case 'h':
	    case 'H':
		if (msg.toLowerCase ().startsWith ("http/")) {
		    /* ignoring header since it looks
		     * like a duplicate responseline
		     */
		    return;
		}
		// fallthrough
	    default:
		throw (new IOException ("Malformed header:" + msg));
	    }
	}
	int j = i;
	while (j > 0 && ((c = msg.charAt (j - 1)) == ' ' || c == '\t'))
	    j--;
	// ok, the header may be empty, so trim away whites.
	String value = msg.substring (i + 1);
	
	/* there are some sites with broken headers
	 * like http://docs1.excite.com/functions.js
	 * which returns lines such as this (20040416) /robo
	 * msg is: 'Cache-control: must-revalidate"'
	 * so we only check for append when in strict mode...
	 */
	if (strictHttp) 
	    append = checkQuotes (value);
	if (!append)
	    value = value.trim ();
	head = new Header (msg.substring (0, j), value);
	header.addHeader (head);
    }

    private boolean checkQuotes (String v) {
	int q = v.indexOf ('"');
	if (q == -1)
	    return false;
	boolean halfquote = false;
	int l = v.length ();
	for (; q < l; q++) {
	    char c = v.charAt (q);
	    if (c == '\\')
		q++;    // skip one...
	    else if (c == '"')
		halfquote = !halfquote;
	}
	return halfquote;
    }

    /** Set the keep alive value to currentkeepalive & keepalive
     * @param keepalive the new keepalive value.
     */
    private void setKeepAlive (boolean keepalive) {
	this.keepalive = (this.keepalive && keepalive);
    }
}

package rabbit.httpio;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import rabbit.http.HttpHeader;
import rabbit.io.BufferHandle;
import rabbit.io.BufferHandler;
import rabbit.io.CacheBufferHandle;
import rabbit.nio.NioHandler;
import rabbit.util.TrafficLogger;

/** A handler that write one http header and reads a response
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HttpResponseReader 
    implements HttpHeaderSentListener, HttpHeaderListener {

    private final SocketChannel channel;
    private final NioHandler nioHandler;
    private final TrafficLogger tl;
    private final BufferHandler bufHandler;
    private final boolean strictHttp;
    private final HttpResponseListener listener;
    private final HttpHeaderSender sender;

    public HttpResponseReader (SocketChannel channel, NioHandler nioHandler, 
			       TrafficLogger tl, BufferHandler bufHandler, 
			       HttpHeader header, boolean fullURI, 
			       boolean strictHttp, 
			       HttpResponseListener listener)
	throws IOException {
	this.channel = channel;
	this.nioHandler = nioHandler;
	this.tl = tl;
	this.bufHandler = bufHandler;
	this.strictHttp = strictHttp;
	this.listener = listener;
	sender = new HttpHeaderSender (channel, nioHandler, tl, 
				       header, fullURI, this);
    }

    public void sendRequestAndWaitForResponse () throws IOException {
	sender.sendHeader ();
    }
    
    public void httpHeaderSent () {
	try {
	    BufferHandle bh = new CacheBufferHandle (bufHandler);
	    HttpHeaderReader reader = 
		new HttpHeaderReader (channel, bh, nioHandler,
				      tl, false, strictHttp, this);
	    reader.readRequest ();
	} catch (IOException e) {
	    failed (e);
	}
    }
    
    public void httpHeaderRead (HttpHeader header, BufferHandle bh, 
				boolean keepalive, boolean isChunked, 
				long dataSize) {
	listener.httpResponse (header, bh, keepalive, isChunked, dataSize);
    }
    
    public void closed () {
	listener.failed (new IOException ("Connection closed"));
    }
    
    public void failed (Exception cause) {
	listener.failed (cause);
    }

    public void timeout () {
	listener.timeout ();
    }
}

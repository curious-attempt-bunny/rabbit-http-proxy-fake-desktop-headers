package rabbit.httpio;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import rabbit.http.HttpHeader;
import rabbit.io.BufferHandle;
import rabbit.io.SimpleBufferHandle;
import rabbit.nio.NioHandler;
import rabbit.util.TrafficLogger;

/** A handler that writes http headers
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HttpHeaderSender implements BlockSentListener {
    private final boolean fullURI;
    private final HttpHeaderSentListener sender;
    private final BlockSender bs;

    /** 
     * @param fullURI if false then try to change header.uri into just the file.
     */
    public HttpHeaderSender (SocketChannel channel, NioHandler nioHandler, 
			     TrafficLogger tl, HttpHeader header,
			     boolean fullURI, HttpHeaderSentListener sender) 
	throws IOException {
	this.fullURI = fullURI;
	this.sender = sender;
	BufferHandle bh = new SimpleBufferHandle (getBuffer (header));
	bs = new BlockSender (channel, nioHandler, tl, bh, false, this);
    }

    public void sendHeader () throws IOException {
	bs.write ();
    }

    private ByteBuffer getBuffer (HttpHeader header) throws IOException {
	String uri = header.getRequestURI ();
	if (header.isRequest () && !header.isSecure () && 
	    !fullURI && uri.charAt (0) != '/') {
	    URL url = new URL (uri);
	    String file = url.getFile ();
	    if (file.equals (""))
		file = "/";
	    header.setRequestURI (file);
	}
	String s = header.toString ();
	byte[] bytes = s.getBytes ("ASCII");
	ByteBuffer buf = ByteBuffer.wrap (bytes);
	header.setRequestURI (uri);		
	return buf;
    }

    public void timeout () {
	sender.timeout ();
    }

    public void failed (Exception cause) {
	sender.failed (cause);
    }

    public void blockSent () {
	sender.httpHeaderSent ();
    }    
}

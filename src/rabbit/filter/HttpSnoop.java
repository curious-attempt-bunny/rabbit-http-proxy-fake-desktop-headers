package rabbit.filter;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import rabbit.http.HttpHeader;
import rabbit.io.BufferHandle;
import rabbit.proxy.ClientResourceHandler;
import rabbit.proxy.ClientResourceListener;
import rabbit.proxy.Connection;
import rabbit.util.SProperties;

/** This is a class that prints the Http headers on the standard out stream.
 */
public class HttpSnoop implements HttpFilter {
    private enum SnoopMode { NORMAL, REQUEST_LINE, FULL }
    private SnoopMode mode;

    /** test if a socket/header combination is valid or return a new HttpHeader.
     * @param socket the SocketChannel that made the request.
     * @param header the actual request made.
     * @param con the Connection handling the request.
     * @return This method always returns null.
     */
    public HttpHeader doHttpInFiltering (SocketChannel socket, 
					 HttpHeader header, Connection con) {
	if (mode == SnoopMode.NORMAL) {
	    System.out.println (con.getRequestLine ());
	} else {
	    System.out.println (header.toString ());
	    if (mode == SnoopMode.FULL) {
		ClientResourceHandler crh = con.getClientResourceHandler ();
		if (crh != null)
		    crh.addContentListener (new ContentLogger (header));
	    }
	}
	return null;
    }

    private static class ContentLogger implements ClientResourceListener {
	private HttpHeader header;
	public ContentLogger (HttpHeader header) {
	    this.header = header;
	}

	public void resouceDataRead (BufferHandle bufHandle) {
	    ByteBuffer buf = bufHandle.getBuffer ();
	    buf = buf.duplicate ();
	    // TODO: format data better
	    System.out.println (header.getRequestLine () + "\n" + buf);				
	}
    }
    
    /** test if a socket/header combination is valid or return a new HttpHeader.
     * @param socket the Socket that made the request.
     * @param header the actual request made.
     * @param con the Connection handling the request.
     * @return This method always returns null.
     */
    public HttpHeader doHttpOutFiltering (SocketChannel socket, 
					  HttpHeader header, Connection con) {
	if (mode == SnoopMode.REQUEST_LINE) {
	    System.out.println (con.getRequestLine () + "\n" + 
				header.getStatusLine ());
	} else {
	    System.out.println (con.getRequestLine () + "\n" + 
				header.toString ());
	}
	return null;
    }

    /** Setup this class with the given properties.
     * @param properties the new configuration of this class.
     */
    public void setup (SProperties properties) {
	String smo = properties.getProperty ("mode", "NORMAL");
	smo = smo.toUpperCase ();
	mode = SnoopMode.valueOf (smo);
    }
}

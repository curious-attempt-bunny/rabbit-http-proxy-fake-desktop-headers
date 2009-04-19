package rabbit.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import rabbit.http.HttpHeader;
import rabbit.nio.NioHandler;
import rabbit.nio.ReadHandler;
import rabbit.util.Counter;
import rabbit.util.SProperties;

/** A class to handle the connections to the net. 
 *  Tries to reuse connections whenever possible.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ConnectionHandler {
    // The logger to use
    private final Logger logger =
	Logger.getLogger (Logger.class.getName ());
    
    // The counter to use.
    private final Counter counter;
    
    // The resolver to use
    private final Resolver resolver;

    // The available connections.
    private final Map<Address, List<WebConnection>> activeConnections;

    // The channels waiting for closing
    private final Map<WebConnection, CloseListener> wc2closer;

    // the keepalivetime.
    private long keepaliveTime = 1000;

    // should we use pipelining...
    private boolean usePipelining = true;

    // the nio handler
    private final NioHandler nioHandler;

    public ConnectionHandler (Counter counter, Resolver resolver, 
			      NioHandler nioHandler) {
	this.counter = counter;
	this.resolver = resolver;
	this.nioHandler = nioHandler;

	activeConnections = 
	    new ConcurrentHashMap<Address, List<WebConnection>> ();
	wc2closer = 
	    new ConcurrentHashMap<WebConnection, CloseListener> ();
    }

    /** Set the keep alive time for this handler.
     * @param milis the keep alive time in miliseconds.
     */
    public void setKeepaliveTime (long milis) {
	keepaliveTime = milis;
    }
    
    /** Get the current keep alive time.
     * @return the keep alive time in miliseconds.
     */
    public long getKeepaliveTime () {
	return keepaliveTime;
    }

    public Map<Address, List<WebConnection>> getActiveConnections () {
	return Collections.unmodifiableMap (activeConnections);
    }

    /** Get a WebConnection for the given header.
     * @param header the HttpHeader containing the URL to connect to.
     * @param wcl the Listener that wants the connection.
     */
    public void getConnection (final HttpHeader header, 
			       final WebConnectionListener wcl) {	
	// TODO: should we use the Host: header if its available? probably...
	String requri = header.getRequestURI ();
	URL url = null;
	try {
	    url = new URL (requri);
	} catch (MalformedURLException e) {
	    wcl.failed (e);
	    return;
	}
	int port = url.getPort () > 0 ? url.getPort () : 80;
	final int rport = resolver.getConnectPort (port);
	
	resolver.getInetAddress (url, new InetAddressListener () {
		public void lookupDone (InetAddress ia) {
		    Address a = new Address (ia, rport);
		    getConnection (header, wcl, a);
		}

		public void unknownHost (Exception e) {
		    wcl.failed (e);
		}		
	    });
    }
    
    private void getConnection (HttpHeader header, 
				WebConnectionListener wcl, 
				Address a) {
	WebConnection wc = null;
	counter.inc ("WebConnections used");
	String method = header.getMethod ();
	
	if (method != null) {
	    // since we should not retry POST (and other) we 
	    // have to get a fresh connection for them..
	    method = method.trim ();
	    if (!(method.equals ("GET") || method.equals ("HEAD"))) {
		wc = new WebConnection (a, counter);
	    } else {	
		wc = getPooledConnection (a, activeConnections);
		if (wc == null)
		    wc = new WebConnection (a, counter);
	    }
	    try {
		wc.connect (nioHandler, wcl);
	    } catch (IOException e) {
		wcl.failed (e);
	    }
	} else {
	    wcl.failed (new IllegalArgumentException ("No method specified: " +
						      header));
	}
    }
    
    private WebConnection 
    getPooledConnection (Address a, Map<Address, List<WebConnection>> conns) {
	synchronized (conns) {
	    List<WebConnection> pool = conns.get (a);
	    if (pool != null) {
		synchronized (pool) {
		    if (pool.size () > 0) 
			return unregister (pool.remove (pool.size () - 1));
		}
	    }
	}
	return null;
    }

    private WebConnection unregister (WebConnection wc) {
	CloseListener closer = null;
	synchronized (wc2closer) {
	    closer = wc2closer.remove (wc);
	}
	if (closer != null)
	    nioHandler.cancel (wc.getChannel (), closer);
	return wc;
    }
    
    private void removeFromPool (WebConnection wc, 
				 Map<Address, List<WebConnection>> conns) {
	synchronized (conns) {
	    List<WebConnection> pool = conns.get (wc.getAddress ());
	    if (pool != null) {
		synchronized (pool) {
		    pool.remove (wc);
		    if (pool.size () == 0)
			conns.remove (wc.getAddress ());
		}
	    }
	}
    }

    /** Return a WebConnection to the pool so that it may be reused.
     * @param wc the WebConnection to return.
     */
    public void releaseConnection (WebConnection wc) {
	counter.inc ("WebConnections released");
	if (!wc.getChannel ().isOpen ()) {
	    return;
	}
	
	Address a = wc.getAddress ();
	if (!wc.getKeepalive ()) {
	    closeWebConnection (wc);
	    return;
	}

	synchronized (wc) {
	    wc.setReleased ();
	}
	synchronized (activeConnections) {
	    List<WebConnection> pool = 
		activeConnections.get (a);
	    if (pool == null) {
		pool = new ArrayList<WebConnection> ();
		activeConnections.put (a, pool);
	    }
	    try {
		CloseListener cl = new CloseListener (wc);
		cl.register ();
		synchronized (wc2closer) {
		    wc2closer.put (wc, cl);
		}
		pool.add (wc);
	    } catch (IOException e) {
		logger.log (Level.WARNING, 
			    "Get IOException when setting up a CloseListener: ",
			    e);
		closeWebConnection (wc);
	    }
	}	
    }

    private void closeWebConnection (WebConnection wc) {
	if (wc == null)
	    return;
	if (!wc.getChannel ().isOpen ())
	    return;
	try {
	    wc.close ();
	} catch (IOException e) {
	    logger.warning ("Failed to close WebConnection: " + wc);
	}
    }
    
    private class CloseListener implements ReadHandler {
	private WebConnection wc;

	public CloseListener (WebConnection wc) throws IOException {
	    this.wc = wc;
	}

	public void register () {
	    nioHandler.waitForRead (wc.getChannel (), this);
	}

	public void read () {
	    closeChannel ();
	}
	
	public void closed () {
	    closeChannel ();
	}
	
	public void timeout () {
	    closeChannel ();
	}

	public Long getTimeout () {
	    return null;
	}

	private void closeChannel () {
	    try {
		synchronized (wc2closer) {
		    wc2closer.remove (wc);
		}
		removeFromPool (wc, activeConnections);
		wc.close ();
	    } catch (IOException e) {
		String err = 
		    "CloseListener: Failed to close web connection: " + e;
		logger.warning (err);
	    }
	}

	public boolean useSeparateThread () {
	    return false;
	}

	public String getDescription () {
	    return "ConnectionHandler$CloseListener: address: " + wc.getAddress ();
	}
    }

    /** Mark a WebConnection ready for pipelining.
     * @param wc the WebConnection to mark ready for pipelining.
     */
    public void markForPipelining (WebConnection wc) {
	if (!usePipelining)
	    return;
	synchronized (wc) {
	    if (!wc.getKeepalive ())
		return;
	    wc.setMayPipeline (true);
	}
    }
    
    public void setup (SProperties config) {
	if (config == null)
	    return;
	String kat = config.getProperty ("keepalivetime", "1000"); 
	try {
	    setKeepaliveTime (Long.parseLong (kat)); 
	} catch (NumberFormatException e) { 
	    String err = 
		"Bad number for ConnectionHandler keepalivetime: '" + kat + "'";
	    logger.warning (err);
	}
	String up = config.get ("usepipelining");
	if (up == null)
	    up = "true";
	usePipelining = up.equalsIgnoreCase ("true");
    }
}
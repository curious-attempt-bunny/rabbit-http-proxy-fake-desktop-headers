package rabbit.client;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.khelekore.rnio.NioHandler;
import org.khelekore.rnio.StatisticsHolder;
import org.khelekore.rnio.impl.BasicStatisticsHolder;
import org.khelekore.rnio.impl.MultiSelectorNioHandler;
import rabbit.http.HttpHeader;
import rabbit.httpio.HttpResponseListener;
import rabbit.httpio.HttpResponseReader;
import rabbit.httpio.SimpleResolver;
import rabbit.httpio.WebConnectionResourceSource;
import rabbit.io.BufferHandle;
import rabbit.io.BufferHandler;
import rabbit.io.CachingBufferHandler;
import rabbit.io.ConnectionHandler;
import rabbit.io.Resolver;
import rabbit.io.WebConnection;
import rabbit.io.WebConnectionListener;
import rabbit.util.Counter;
import rabbit.util.SimpleTrafficLogger;
import rabbit.util.TrafficLogger;

/** A class for doing http requests.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ClientBase {
    private final Logger logger = Logger.getLogger (getClass ().getName ());
    private final ConnectionHandler connectionHandler;
    private final Counter counter = new Counter ();
    private final NioHandler nioHandler;
    private final TrafficLogger trafficLogger = new SimpleTrafficLogger ();
    private final BufferHandler bufHandler;

    /** Create a new ClientBase.
     */
    public ClientBase () throws IOException {
	ExecutorService es = Executors.newCachedThreadPool ();
	StatisticsHolder sh = new BasicStatisticsHolder ();
	nioHandler = new MultiSelectorNioHandler (es, sh, 4);
	nioHandler.start ();
	Resolver resolver = new SimpleResolver (nioHandler);
	connectionHandler =
	    new ConnectionHandler (counter, resolver, nioHandler);

	bufHandler = new CachingBufferHandler ();
    }

    /** Submit a new request, using the given method to the given url.
     * @param method HEAD or GET or POST or ... 
     * @param url the url to do the http request against.
     */
    public HttpHeader getRequest (String method, String url) 
	throws IOException {
	URL u = new URL (url);
	HttpHeader ret = new HttpHeader ();
	ret.setStatusLine (method + " " + url + " HTTP/1.1");
	ret.setHeader ("Host" , u.getHost ());
	ret.setHeader ("User-Agent", "rabbit client library");
	return ret;
    }
    
    public ConnectionHandler getConnectionHandler () {
	return connectionHandler;
    }

    public NioHandler getNioHandler () {
	return nioHandler;
    }

    public Logger getLogger () {
	return logger;
    }

    /** Shutdown this client handler.
     */ 
    public void shutdown () {
	nioHandler.shutdown ();
    }

    /** Send a request and let the client be notified on response. 
     */
    public void sendRequest (HttpHeader request, ClientListener client) 
	throws IOException {
	WebConnectionListener wcl = new WCL (request, client);
	connectionHandler.getConnection (request, wcl);
    }

    private void handleTimeout (HttpHeader request, ClientListener client) {
	client.handleTimeout (request);
    }

    private void handleFailure (HttpHeader request, ClientListener client, 
				Exception e) {
	client.handleFailure (request, e);
    }
 
    private abstract class BaseAsyncListener {
	protected HttpHeader request;
	protected ClientListener client;
	
	public BaseAsyncListener (HttpHeader request, ClientListener client) {
	    this.request = request;
	    this.client = client;
	}	

	public void timeout () {
	    handleTimeout (request, client);
	}

	public void failed (Exception e) {
	    handleFailure (request, client, e);
	}
    }

    private class WCL extends BaseAsyncListener 
	implements WebConnectionListener {
	
	public WCL (HttpHeader request, ClientListener client) {
	    super (request, client);
	}
	
	public void connectionEstablished (WebConnection wc) {
	    sendRequest (request, client, wc);
	}
    }

    private void sendRequest (HttpHeader request, ClientListener client, 
			      WebConnection wc) {
	HttpResponseListener hrl = new HRL (request, client, wc);
	try {
	    HttpResponseReader rr = 
		new HttpResponseReader (wc.getChannel (), nioHandler,
					trafficLogger, bufHandler, 
					request, true, true, hrl);
	    rr.sendRequestAndWaitForResponse ();
	} catch (IOException e) {
	    handleFailure (request, client, e);
	}
    }
    
    private class HRL extends BaseAsyncListener 
	implements HttpResponseListener {
	private final WebConnection wc;

	public HRL (HttpHeader request, ClientListener client, 
		    WebConnection wc) {
	    super (request, client);
	    this.wc = wc;
	}
	
	public void httpResponse (HttpHeader response, 
				  BufferHandle bufferHandle, 
				  boolean keepalive, boolean isChunked, 
				  long dataSize) {
	    int status = Integer.parseInt (response.getStatusCode ());
	    if (client.followRedirects () && isRedirect (status)) {
		connectionHandler.releaseConnection (wc);
		String loc = response.getHeader ("Location");
		client.redirected (request, loc, ClientBase.this);
	    } else {
		WebConnectionResourceSource wrs = 
		    getWebConnectionResouceSource (wc, bufferHandle, 
						   isChunked, dataSize);
		client.handleResponse (request, response, wrs);
	    }
	}
    }

    /** Check if the status code is a redirect code.
     */ 
    private boolean isRedirect (int status) {
	return status == 301 || status == 302 || 
	    status == 303 || status == 307;
    }

    /** Create the url that the response redirected the request to.
     */
    public URL getRedirectedURL (HttpHeader request, String location) 
	throws IOException {
	URL u = new URL (request.getRequestURI ());
	return new URL (u, location);
    }

    private WebConnectionResourceSource 
    getWebConnectionResouceSource (WebConnection wc, BufferHandle bufferHandle,
				   boolean isChunked, long dataSize) {
	WebConnectionResourceSource wrs = 
	    new WebConnectionResourceSource (connectionHandler, nioHandler, 
					     wc, bufferHandle, trafficLogger, 
					     isChunked, dataSize, true);
	return wrs;
    }
}

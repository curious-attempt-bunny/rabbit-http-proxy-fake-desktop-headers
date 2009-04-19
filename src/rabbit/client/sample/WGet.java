package rabbit.client.sample;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import rabbit.client.ClientBase;
import rabbit.client.ClientListenerAdapter;
import rabbit.client.CountingClientBaseStopper;
import rabbit.client.FileSaver;
import rabbit.http.HttpHeader;
import rabbit.httpio.WebConnectionResourceSource;

/** A class to download a set of resources.
 *  Given a set of urls this class will download all of them concurrently
 *  using a standard ClientBase.
 *  This is mostly an example of how to use the rabbit client classes.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class WGet {
    private final ClientBase clientBase;
    private final CountingClientBaseStopper ccbs;

    /** Download all urls given in the args arrays.
     */ 
    public static void main (String[] args) {
	try {
	    WGet wget = new WGet ();
	    wget.get (args);
	} catch (IOException e) {
	    e.printStackTrace ();
	}
    }
    
    /** Create a new WGet that can be used to download resources.
     */ 
    public WGet () throws IOException {
	clientBase = new ClientBase ();
	ccbs = new CountingClientBaseStopper (clientBase);
    }

    /** Add a set of urls to download.
     */
    public void get (String[] urls) throws IOException {
	for (String url : urls)
	    get (url);
    }

    /** Add an url to the set of urls to be downloaded
     */ 
    public void get (String url) throws IOException {
	ccbs.sendRequest (clientBase.getRequest ("GET", url),
			  new WGetListener ());
    }

    private class WGetListener extends ClientListenerAdapter {   
	@Override public void redirectedTo (String url) throws IOException {
	    get (url);
	}

	@Override 
	public void handleResponse (HttpHeader request, HttpHeader response, 
				    WebConnectionResourceSource wrs) {
	    try {
		File f = new File (getFileName (request));
		if (f.exists ())
		    throw new IOException ("File already exists: " + 
					   f.getName ());
		FileSaver blockHandler = 
		    new FileSaver (request, clientBase, this, wrs, f);
		wrs.addBlockListener (blockHandler);
	    } catch (IOException e) {
		wrs.release ();
		handleFailure (request, e);
	    }
	}

	@Override public void requestDone (HttpHeader request) {
	    ccbs.requestDone ();
	}
    }

    private String getFileName (HttpHeader request) throws IOException {
	URL u = new URL (request.getRequestURI ());
	String s = u.getFile ();
	int i = s.lastIndexOf ('/');
	if (i > -1) 
	    s = s.substring (i + 1);
	if (s.equals (""))
	    return "index.html";
	return s;
    }
}

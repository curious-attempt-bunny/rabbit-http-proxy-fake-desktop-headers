package rabbit.proxy;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import rabbit.filter.HttpFilter;
import rabbit.http.HttpHeader;
import rabbit.util.Config;

/** A class to load and run the HttpFilters.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class HttpHeaderFilterer {
    private List<HttpFilter> httpInFilters;
    private List<HttpFilter> httpOutFilters;
    
    public HttpHeaderFilterer (String in, String out, 
			       Config config, HttpProxy proxy) {
	httpInFilters = new ArrayList<HttpFilter> ();
	loadHttpFilters (in, httpInFilters, config, proxy);
	
	httpOutFilters = new ArrayList<HttpFilter> ();
	loadHttpFilters (out, httpOutFilters, config, proxy);
    }

    /** Runs all input filters on the given header. 
     * @param con the Connection handling the request
     * @param channel the SocketChannel for the client
     * @param in the request. 
     * @return null if all is ok, a HttpHeader if this request is blocked.
     */
    public HttpHeader filterHttpIn (Connection con, 
				    SocketChannel channel, HttpHeader in) {
	int s = httpInFilters.size ();
	for (int i = 0; i < s; i++) {
	    HttpFilter hf = httpInFilters.get (i);
	    HttpHeader badresponse = 
		hf.doHttpInFiltering (channel, in, con);
	    if (badresponse != null)
		return badresponse;	    
	}
	return null;
    }

    /** Runs all output filters on the given header. 
     * @param con the Connection handling the request
     * @param channel the SocketChannel for the client
     * @param in the response. 
     * @return null if all is ok, a HttpHeader if this request is blocked.
     */
    public HttpHeader filterHttpOut (Connection con, 
				     SocketChannel channel, HttpHeader in) {
	int s = httpOutFilters.size ();
	for (int i = 0; i < s; i++) {
	    HttpFilter hf = httpOutFilters.get (i);
	    HttpHeader badresponse = 
		hf.doHttpOutFiltering (channel, in, con);
	    if (badresponse != null)
		return badresponse;	    
	}
	return null;
    }

    private void loadHttpFilters (String filters, List<HttpFilter> ls,
				  Config config, HttpProxy proxy) {
	String[] filterArray = filters.split (",");
	for (int i = 0; i < filterArray.length; i++) {
	    String className = filterArray[i];
	    Logger log = Logger.getLogger (getClass ().getName ());
	    try {
		className = className.trim ();
		Class<? extends HttpFilter> cls = 
		    Class.forName (className).asSubclass (HttpFilter.class);
		HttpFilter hf = cls.newInstance ();
		hf.setup (config.getProperties (className));
		ls.add (hf);
	    } catch (ClassNotFoundException ex) {
		log.log (Level.WARNING, 
			 "Could not load http filter class: '" + 
			 className + "'", ex);
	    } catch (InstantiationException ex) {
		log.log (Level.WARNING, 
			 "Could not instansiate http filter: '" + 
			 className + "'", ex);
	    } catch (IllegalAccessException ex) {
		log.log (Level.WARNING, 
			 "Could not access http filter: '" + 
			 className + "'", ex);
	    }
	}
    }

    public List<HttpFilter> getHttpInFilters () {
	return httpInFilters;
    }

    public List<HttpFilter> getHttpOutFilters () {
	return httpOutFilters;
    }
}

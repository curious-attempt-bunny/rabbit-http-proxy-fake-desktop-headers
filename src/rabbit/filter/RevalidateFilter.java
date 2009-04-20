package rabbit.filter;

import java.nio.channels.SocketChannel;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import rabbit.http.HttpHeader;
import rabbit.proxy.Connection;
import rabbit.util.SProperties;

/** This is a class that makes all requests (matching a few criterias) use revalidation
 *  even if there is a usable resource in the cache.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class RevalidateFilter implements HttpFilter {
    private boolean alwaysRevalidate = false;
    private Pattern revalidatePattern = null;

    public HttpHeader doHttpInFiltering (SocketChannel socket, 
					 HttpHeader header, 
					 Connection con) {
	if (alwaysRevalidate || needsRevalidation (header.getRequestURI ())) {
	    con.setMustRevalidate (true);
	}
	return null;
    }

    private boolean needsRevalidation (String uri) {
	Matcher m = revalidatePattern.matcher (uri);
	return m.find ();
    }

    public HttpHeader doHttpOutFiltering (SocketChannel socket, 
					  HttpHeader header, 
					  Connection con) {
	// nothing to do
	return null;
    }

    public void setup (SProperties properties) {
	String always = properties.getProperty ("alwaysrevalidate", "false");
	alwaysRevalidate = Boolean.valueOf (always);
	if (!alwaysRevalidate) {
	    String mustRevalidate = properties.getProperty ("revalidate");
	    if (mustRevalidate == null) {
		Logger logger = Logger.getLogger (getClass ().getName ());
		logger.warning ("alwaysRevalidate is off and no revalidate " + 
				"patterns found, filter is useless.");
		return;
	    }
	    revalidatePattern = Pattern.compile (mustRevalidate);
	}
    }
}

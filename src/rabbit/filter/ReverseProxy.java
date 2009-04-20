package rabbit.filter;

import java.nio.channels.SocketChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import rabbit.http.HttpHeader;
import rabbit.proxy.Connection;
import rabbit.util.SProperties;

/** This is a filter that set up rabbit for reverse proxying. 
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ReverseProxy implements HttpFilter {
    private String matcher = null;
    private String replacer = null;
    private Pattern deny = null;
    private boolean allowMeta = false;

    /** test if a socket/header combination is valid or return a new HttpHeader.
     * @param socket the SocketChannel that made the request.
     * @param header the actual request made.
     * @param con the Connection handling the request.
     * @return null if everything is fine or a HttpHeader 
     *         describing the error (like a 403).
     */
    public HttpHeader doHttpInFiltering (SocketChannel socket, 
					 HttpHeader header, Connection con) {
	String s = header.getRequestURI ();
	if (deny != null) {
	    Matcher m = deny.matcher (s);
	    if (m.matches () && allowMeta) {
		String metaStart = "http://" + 
		    con.getProxy ().getHost ().getHostName () + ":" + 
		    con.getProxy ().getPort () + "/";
		if (!s.startsWith (metaStart)) {
		    return con.getHttpGenerator ().get403 ();
		}
	    }
	}
	if (matcher != null && replacer != null && 
	    s != null && s.length () > 0 && s.charAt (0) == '/') {
	    String newRequest = s.replaceAll (matcher, replacer);
	    header.setRequestURI (newRequest);
	}
	return null;
    }
    
    /** test if a socket/header combination is valid or return a new HttpHeader.
     * @param socket the SocketChannel that made the request.
     * @param header the actual request made.
     * @param con the Connection handling the request.
     * @return null if everything is fine or a HttpHeader 
     *         describing the error (like a 403).
     */
    public HttpHeader doHttpOutFiltering (SocketChannel socket, 
					  HttpHeader header, Connection con) {
	return null;
    }

    /** Setup this class with the given properties.
     * @param logger the Logger to output errors/warnings on.
     * @param properties the new configuration of this class.
     */
    public void setup (SProperties properties) {
	matcher = properties.getProperty ("transformMatch", "");
	replacer = properties.getProperty ("transformTo", "");
	String denyString = properties.getProperty ("deny");
	if (denyString != null)
	    deny = Pattern.compile (denyString);
	allowMeta = 
	    properties.getProperty ("allowMeta", "true").equalsIgnoreCase ("true");
    }
}

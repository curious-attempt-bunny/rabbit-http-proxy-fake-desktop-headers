package rabbit.filter;

import java.nio.channels.SocketChannel;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import rabbit.http.HttpHeader;
import rabbit.proxy.Connection;
import rabbit.util.SProperties;
import rabbit.util.PatternHelper;

/** This is a class that blocks access to certain part of the www.
 *  You can either specify a deny filter, using blockURLmatching
 *  or you can specify an accept filter, using allowURLmatching.
 *
 *  If you specify an accept filter, then no other urls will be accepted. 
 */
public class BlockFilter implements HttpFilter {
    private Pattern blockPattern;
    private Pattern allowPattern;
   
    /** Create a new Blockfilter
     */
    public BlockFilter () {
    }
    
    /** test if a socket/header combination is valid or return a new HttpHeader.
     * @param socket the SocketChannel that made the request.
     * @param header the actual request made.
     * @param con the Connection handling the request.
     * @return if the url of the request is matched, then a 403 is returned, 
     *         otherwise null is returned.
     */
    public HttpHeader doHttpInFiltering (SocketChannel socket, 
					 HttpHeader header, Connection con) {
	if (allowPattern != null) {
	    Matcher m = allowPattern.matcher (header.getRequestURI());
	    if (m.find ()) 
		return null;
	    return con.getHttpGenerator ().get403 ();
        }

	if (blockPattern == null)
	    return null;
	Matcher m = blockPattern.matcher (header.getRequestURI ());
	if (m.find ())
	    return con.getHttpGenerator ().get403 ();
	return null;
    }
    
    /** test if a socket/header combination is valid or return a new HttpHeader.
     * @param socket the SocketChannel that made the request.
     * @param header the actual request made.
     * @param con the Connection handling the request.
     * @return This method always returns null.
     */
    public HttpHeader doHttpOutFiltering (SocketChannel socket, 
					  HttpHeader header, Connection con) {
	return null;
    }

    /** Setup this class with the given properties.
     * @param properties the new configuration of this class.
     */
    public void setup (SProperties properties) {
	PatternHelper ph = new PatternHelper ();
	blockPattern = ph.getPattern (properties, "blockURLmatching", 
				      "BlockFilter: bad pattern: ");
        allowPattern = ph.getPattern (properties, "allowURLmatching", 
				      "AllowFilter: bad pattern: ");
    }
}

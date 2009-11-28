package rabbit.filter;

import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import rabbit.http.HttpHeader;
import rabbit.proxy.Connection;
import rabbit.proxy.HttpGenerator;
import rabbit.util.SProperties;

/** This is a filter that requires users to use 
 *  proxy-authentication, using users in a sql table.
 *
 *  Will read the following parameters from the config file: 
 *  <ul>
 *  <li>driver 
 *  <li>url
 *  <li>user
 *  <li>password
 *  <li>select - the sql query to run
 *  <li>cachetime (minutes)
 *  <li>one_ip_only - restrict access so that users can only use one ip
 *  </ul>
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SQLProxyAuth implements HttpFilter {
    private String url = null;
    private String dbuser = null;
    private String dbpwd = null;
    private String select = null;
    private int cacheTime = 0;
    private boolean oneIpOnly = true;

    private static final String DEFAULT_SELECT = 
	"select password from users where username = ?";

    private java.sql.Connection db = null;
    private final Logger logger = Logger.getLogger (getClass ().getName ());

    /** Username to user info */
    private Map<String, UserInfo> cache = new HashMap<String, UserInfo> ();

    private synchronized void initConnection () throws SQLException {
	db = DriverManager.getConnection (url, dbuser, dbpwd);
    }

    /** test if a socket/header combination is valid or return a new HttpHeader.
     *  Check that the user has been authenticate..
     * 
     *  Check if we want to cache user info...
     * 
     * @param socket the SocketChannel that made the request.
     * @param header the actual request made.
     * @param con the Connection handling the request.
     * @return null if everything is fine or a HttpHeader 
     *         describing the error (like a 403).
     */
    public HttpHeader doHttpInFiltering (SocketChannel socket, 
					 HttpHeader header, 
					 Connection con) {
	if (con.getMeta ())
	    return null;
	String username = con.getUserName ();
	String pwd = con.getPassword ();
	if (username == null || pwd == null) 
	    return getError (con, header);
	try {
	    if (validPassword (username, pwd, con.getChannel ()))
		return null;
	} catch (SQLException e) {
	    logger.log (Level.WARNING, "Exception when trying to get user: " + e);
	    closeDB (con);
	}
	return getError (con, header);
    }

    /** test if a socket/header combination is valid or return a new HttpHeader.
     * @param socket the Socket that made the request.
     * @param header the actual request made.
     * @param con the Connection handling the request.
     * @return This method always returns null.
     */
    public HttpHeader doHttpOutFiltering (SocketChannel socket, 
					  HttpHeader header, Connection con) {
	return null;
    }

    private HttpHeader getError (Connection con, HttpHeader header) {
	HttpGenerator hg = con.getHttpGenerator ();
	try {
	    return hg.get407 ("internet", new URL (header.getRequestURI ()));
	} catch (MalformedURLException e) {
	    logger.warning ("Bad url: " + e);
	    return hg.get407 ("internet", null);
	}
    }

    private synchronized void closeDB (Connection con) {
	if (db == null)
	    return;
	try {
	    db.close ();
	} catch (SQLException e) {
	    logger.log (Level.WARNING, "failed to close database", e);
	}
	db = null;
    } 

    private static class UserInfo {
	private final String pwd;
	private final long timeout;
	private final SocketAddress sa;

	public UserInfo (String pwd, long timeout, SocketAddress sa) {
	    this.pwd = pwd;
	    this.timeout = timeout;
	    this.sa = sa;
	}

	public boolean stillValid () {
	    long now = System.currentTimeMillis ();
	    return timeout > now;
	}
	
	public boolean correctPassWord (String userPassword) {
	    return userPassword.equals (pwd);
	}
	
	public boolean correctSocketAddress (SocketAddress sa) {
	    return this.sa.equals (sa);
	}
    }
    
    private boolean validPassword (String username, String pwd, 
				   SocketChannel channel) 
	throws SQLException {
	UserInfo ce = getUserInfo (username, channel);
	if (ce == null)
	    return false;
	if (!ce.correctPassWord (pwd))
	    return false;
	if (oneIpOnly) {
	    Socket socket = channel.socket ();
	    if (!ce.correctSocketAddress (socket.getRemoteSocketAddress ()))
		return false;
	}
	return true;
    }

    private UserInfo getUserInfo (String username, 
				  SocketChannel channel) 
	throws SQLException {
	UserInfo resp;
	synchronized (this) {
	    resp = cache.get (username);
	}
	if (resp != null) {
	    if (cacheTime <= 0)
		return resp;
	    if (resp.stillValid ())
		return resp;
	}

	synchronized (this) {
	    if (db == null)
		initConnection ();
	}
	PreparedStatement ps = null;
	try {
	    synchronized (this) {
		ps = db.prepareStatement (select);
	    }
	    ps.setString (1, username);
	    ResultSet rs = ps.executeQuery ();
	    try {
		if (rs.next ()) {
		    String ret = rs.getString (1);
		    long timeout = 
			System.currentTimeMillis () + 60000 * cacheTime;
		    SocketAddress sa = 
			channel.socket ().getRemoteSocketAddress ();
		    UserInfo ce = new UserInfo (ret, timeout, sa);
		    synchronized (this) {
			if (cacheTime > 0)
			    cache.put (username, ce);
		    }
		    return ce;
		}
	    } finally {
		rs.close ();
	    }
	    return null;
	} finally {
	    if (ps != null) {
		try {
		    ps.close ();
		} catch (SQLException e) {
		    logger.log (Level.WARNING, "Failed to close statement", e);
		}		
	    }
	} 
    }

    /** Setup this class with the given properties.
     * @param properties the new configuration of this class.
     */
    public void setup (SProperties properties) {
	String driver = properties.getProperty ("driver");
	try {
	    Class.forName (driver);
	} catch (ClassNotFoundException e) {
	    logger.log (Level.WARNING, "Failed to load driver: " + driver, e);
	}
	synchronized (this) {
	    url = properties.getProperty ("url");
	    dbuser = properties.getProperty ("user");
	    dbpwd = properties.getProperty ("password");
	    select = properties.getProperty ("select", DEFAULT_SELECT);
	    String ct = properties.getProperty ("cachetime", "0");
	    cacheTime = Integer.parseInt (ct);
	    String ra = properties.getProperty ("one_ip_only", "true");
	    oneIpOnly = Boolean.valueOf (ra);
	}
    }    
}

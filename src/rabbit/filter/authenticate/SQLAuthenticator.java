package rabbit.filter.authenticate;

import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import rabbit.util.SProperties;

/** An authenticator that checks the username/password against
 *  an sql database.
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
public class SQLAuthenticator implements Authenticator {
    private final String url;
    private final String dbuser;
    private final String dbpwd;
    private final String select;
    private final int cacheTime;
    private final boolean oneIpOnly;

    /** Username to user info */
    private final Map<String, AuthUserInfo> cache = 
	new ConcurrentHashMap<String, AuthUserInfo> ();
    private final Logger logger = Logger.getLogger (getClass ().getName ());
    private static final String DEFAULT_SELECT = 
	"select password from users where username = ?";
    
    private final AtomicReference<Connection> db = 
	new AtomicReference<Connection> (null);

    public SQLAuthenticator (SProperties props) {
	String driver = props.getProperty ("driver");
	try {
	    Class.forName (driver);
	} catch (ClassNotFoundException e) {
	    throw new IllegalStateException ("Failed to load driver: " + 
					     driver, e);
	}
	url = props.getProperty ("url");
	dbuser = props.getProperty ("user");
	dbpwd = props.getProperty ("password");
	select = props.getProperty ("select", DEFAULT_SELECT);
	String ct = props.getProperty ("cachetime", "0");
	cacheTime = Integer.parseInt (ct);
	String ra = props.getProperty ("one_ip_only", "true");
	oneIpOnly = Boolean.valueOf (ra);
    }

    public boolean authenticate (String user, String pwd, SocketChannel channel) {
	try {
	    return validPassword (user, pwd, channel);
	} catch (SQLException e) {
	    logger.log (Level.WARNING, "Exception when trying to authenticate " + 
			"user: " + e);
	    closeDB ();
	}
	return false;
    }

    private void initConnection () throws SQLException {
	Connection con = DriverManager.getConnection (url, dbuser, dbpwd);
	if (!db.compareAndSet (null, con)) {
	    closeDB (con);
	}
    }

    private void closeDB () {
	closeDB (db.getAndSet (null));
    }

    private void closeDB (Connection con) {
	if (con == null)
	    return;
	try {
	    con.close ();
	} catch (SQLException e) {
	    logger.log (Level.WARNING, "failed to close database", e);
	}
    } 

    private boolean validPassword (String user, String pwd, 
				   SocketChannel channel) 
	throws SQLException {
	AuthUserInfo ce = getUserInfo (user, channel);
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

    private AuthUserInfo getUserInfo (String username, 
				      SocketChannel channel) 
	throws SQLException {
	AuthUserInfo resp = cache.get (username);
	if (resp != null) {
	    if (cacheTime <= 0)
		return resp;
	    if (resp.stillValid ())
		return resp;
	}

	Connection con = db.get ();
	if (con == null)
	    initConnection ();
	PreparedStatement ps = con.prepareStatement (select);
	try {
	    ps.setString (1, username);
	    ResultSet rs = ps.executeQuery ();
	    try {
		if (rs.next ()) {
		    String ret = rs.getString (1);
		    long timeout = 
			System.currentTimeMillis () + 60000 * cacheTime;
		    SocketAddress sa = 
			channel.socket ().getRemoteSocketAddress ();
		    AuthUserInfo ce = new AuthUserInfo (ret, timeout, sa);
		    if (cacheTime > 0)
			cache.put (username, ce);
		    return ce;
		}
	    } finally {
		rs.close ();
	    }
	} finally {
	    ps.close ();
	} 
	return null;
    }
}

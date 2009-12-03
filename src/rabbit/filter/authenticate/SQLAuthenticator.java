package rabbit.filter.authenticate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import rabbit.http.HttpHeader;
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
    }

    public String getToken (HttpHeader header, rabbit.proxy.Connection con) {
	return con.getPassword ();
    }

    public boolean authenticate (String user, String token) {
	try {
	    String pwd = getDbPassword (user);
	    if (pwd == null)
		return false;
	    return pwd.equals (token);
	} catch (SQLException e) {
	    e.printStackTrace ();
	    logger.log (Level.WARNING, "Exception when trying to authenticate " +
			"user: " + e);
	    closeDB ();
	}
	return false;
    }

    private Connection initConnection () throws SQLException {
	Connection con = null;
	if (dbuser != null && !dbuser.isEmpty () &&
	    dbpwd != null && !dbpwd.isEmpty ())
	    con = DriverManager.getConnection (url, dbuser, dbpwd);
	else
	    con = DriverManager.getConnection (url);
	if (con == null)
	    throw new SQLException ("Failed to establish conneciton: " + url);
	if (!db.compareAndSet (null, con)) {
	    closeDB (con);
	}
	return con;
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

    private String getDbPassword (String username) throws SQLException {
	Connection con = db.get ();
	if (con == null)
	    con = initConnection ();
	PreparedStatement ps = con.prepareStatement (select);
	try {
	    ps.setString (1, username);
	    ResultSet rs = ps.executeQuery ();
	    try {
		if (rs.next ()) {
		    String ret = rs.getString (1);
		    return ret;
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

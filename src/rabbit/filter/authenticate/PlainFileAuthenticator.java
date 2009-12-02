package rabbit.filter.authenticate;

import java.nio.channels.SocketChannel;
import rabbit.util.SProperties;
import rabbit.util.SimpleUserHandler;

/** An authenticator that reads username and passwords from a plain 
 *  text file.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class PlainFileAuthenticator implements Authenticator {
    private final SimpleUserHandler userHandler;
    
    public PlainFileAuthenticator (SProperties props) {
	String userFile = props.getProperty ("userfile", "conf/allowed");
	userHandler = new SimpleUserHandler ();
	userHandler.setFile (userFile);
    }

    public boolean authenticate (String user, String pwd, SocketChannel channel) {
	return userHandler.isValidUser (user, pwd);
    }
}

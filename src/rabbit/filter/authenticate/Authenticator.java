package rabbit.filter.authenticate;

import java.nio.channels.SocketChannel;

/** Something that can authenticate users using some kind of database. 
 */
public interface Authenticator {

    /** Try to authenticate the user.
     * @param user the username
     * @param pwd the password of the user
     * @param channel the socket channel the user is currently using
     * @return true if authentication succeeded, false otherwise.
     */
    boolean authenticate (String user, String pwd, SocketChannel channel);
}

package rabbit.io;

import java.net.InetAddress;

/** Used to control how WebConection bind to the local sockets.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface SocketBinder {
    /** Get the port number to bind to, use 0 to let the system pick a port.
     */
    int getPort ();

    /** Get the inet address to bind to, return null to get the wildcard.
     */
    InetAddress getInetAddress ();
}

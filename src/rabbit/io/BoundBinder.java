package rabbit.io;

import java.net.InetAddress;

/** A binder that will bind to a specific InetAddress
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class BoundBinder implements SocketBinder {
    private final InetAddress ia;

    public BoundBinder (InetAddress ia) {
	this.ia = ia;
    }
    
    public int getPort () {
	return 0;
    }

    public InetAddress getInetAddress () {
	return ia;
    }
}

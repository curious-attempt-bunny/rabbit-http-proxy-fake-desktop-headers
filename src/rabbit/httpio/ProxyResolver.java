package rabbit.httpio;

import java.net.InetAddress;
import java.net.URL;
import rabbit.io.InetAddressListener;
import rabbit.io.Resolver;

/** A resolver that always return the proxy address.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ProxyResolver implements Resolver {
    /** Adress of connected proxy. */
    private final InetAddress proxy;
    /** Port of the connected proxy. */
    private final int port;
    /** The proxy auth token we will use. */
    private final String auth;

    public ProxyResolver (InetAddress proxy, int port, String auth) {
	this.proxy = proxy;
	this.port = port;
	this.auth = auth;
    }
    
    public void getInetAddress (URL url, InetAddressListener listener) {
	listener.lookupDone (proxy);
    }

    public int getConnectPort (int wantedPort) {
	return port;
    } 

    public boolean isProxyConnected () {
	return true;
    }

    public String getProxyAuthString () {
	return auth;
    }
}
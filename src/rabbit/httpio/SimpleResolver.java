package rabbit.httpio;

import java.net.URL;
import org.khelekore.rnio.NioHandler;
import org.khelekore.rnio.impl.DefaultTaskIdentifier;
import rabbit.dns.DNSHandler;
import rabbit.io.InetAddressListener;
import rabbit.io.Resolver;

/** A simple resolver that uses the given dns handler.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SimpleResolver implements Resolver {
    private final DNSHandler dnsHandler;
    private final NioHandler tr;

    public SimpleResolver (NioHandler tr, DNSHandler dnsHandler) {
	this.dnsHandler = dnsHandler;
	this.tr = tr;
    }

    public void getInetAddress (URL url, InetAddressListener listener) {
	String groupId = getClass ().getSimpleName ();
	tr.runThreadTask (new ResolvRunner (dnsHandler, url, listener), 
			  new DefaultTaskIdentifier (groupId, url.toString ()));
    }

    public int getConnectPort (int port) {
	return port;
    } 

    public boolean isProxyConnected () {
	return false;
    }

    public String getProxyAuthString () {
	return null;
    }
}

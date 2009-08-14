package rabbit.httpio;

import java.net.URL;
import rabbit.dns.DNSHandler;
import rabbit.dns.DNSJavaHandler;
import rabbit.io.InetAddressListener;
import rabbit.io.Resolver;
import rabbit.nio.DefaultTaskIdentifier;
import rabbit.nio.NioHandler;

/** A simple resolver that uses the dnsjava resolver. 
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SimpleResolver implements Resolver {
    private final DNSHandler dnsHandler;
    private final NioHandler tr;

    public SimpleResolver (NioHandler tr) {
	DNSJavaHandler jh = new DNSJavaHandler ();
	jh.setup (null);
	dnsHandler = jh;
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

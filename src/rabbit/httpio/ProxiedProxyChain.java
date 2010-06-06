package rabbit.httpio;

import java.net.InetAddress;
import rabbit.io.ProxyChain;
import rabbit.io.Resolver;

/** An implementation of ProxyChain that always goes through some 
 *  other proxy
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ProxiedProxyChain implements ProxyChain {
    private final Resolver resolver;

    public ProxiedProxyChain (InetAddress proxy, int port, String proxyAuth) {
	resolver = new ProxyResolver (proxy, port, proxyAuth);
    }

    public Resolver getResolver (String url) {
	return resolver;
    }
}

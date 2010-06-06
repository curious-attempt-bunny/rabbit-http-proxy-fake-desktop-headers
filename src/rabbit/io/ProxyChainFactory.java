package rabbit.io;

import java.util.logging.Logger;
import org.khelekore.rnio.NioHandler;
import rabbit.dns.DNSHandler;
import rabbit.util.SProperties;

/** A constructor of ProxyChain:s.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface ProxyChainFactory {
    /** Create a ProxyChain given the properties. */
    ProxyChain getProxyChain (SProperties props, 
			      NioHandler nio, 
			      DNSHandler dnsHandler,
			      Logger logger);
}
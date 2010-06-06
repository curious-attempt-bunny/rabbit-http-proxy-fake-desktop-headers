package rabbit.io;

import rabbit.util.SProperties;

/** A constructor of ProxyChain:s.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface ProxyChainFactory {
    /** Create a ProxyChain given the properties. */
    ProxyChain getProxyChain (SProperties props);
}
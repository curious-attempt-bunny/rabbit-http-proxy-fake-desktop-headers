package rabbit.nio;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.logging.Logger;

/** A helper class that can be used to find InetAddresses of a
 *  specific type (ipv4 or ipv6) and connected to a specified 
 *  network interface.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class InetAddressHelper {

    private static NetworkInterface getNetworkInterface (String name) 
	throws SocketException {
	if (name == null) 
	    return null;
	NetworkInterface iface = NetworkInterface.getByName (name);
	if (iface == null) {
	    Logger logger = 
		Logger.getLogger (InetAddressHelper.class.getName ());
	    logger.severe ("Failed to find network interface: '" + name + "'");
	}
	return iface;
    }

    /** Find an InetAddress on the given interface and with the correct type.
     * @param interfaceName the name of the network interface, like "eth0"
     * @param type either ipv4 or ip46
     */
    public static InetAddress getInetAddress (String interfaceName, 
					      String type) 
	throws IOException {
	NetworkInterface iface = getNetworkInterface (interfaceName);
	if (iface == null)
	    return null;
	return getInetAddress (iface, type);
    }

    private static InetAddress getInetAddress (NetworkInterface iface, 
					       String type)
	throws IOException {
	boolean useIpv4 = "ipv4".equals (type);
	    
	Enumeration<InetAddress> e = iface.getInetAddresses ();
	while (e.hasMoreElements ()) {
	    InetAddress ia = e.nextElement ();
	    if (useIpv4 && ia.getClass () == Inet4Address.class)
		return ia;
	    else if (!useIpv4 && ia.getClass () == Inet6Address.class)
		return ia;
	}
	throw new IOException ("Failed to find " + type + " address for " + 
			       "interface: " + iface.getName ());
    }
}
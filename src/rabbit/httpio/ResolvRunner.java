package rabbit.httpio;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import rabbit.dns.DNSHandler;
import rabbit.io.InetAddressListener;

/** A dns lookup class that runs in the background.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ResolvRunner implements Runnable {
    private final DNSHandler dnsHandler;
    private final URL url;
    private final InetAddressListener ial;

    public ResolvRunner (DNSHandler dnsHandler, 
			 URL url, InetAddressListener ial) {
	this.dnsHandler = dnsHandler;
	this.url = url;
	this.ial = ial;
    }

    /** Run a dns lookup and then notifies the listener on the selector thread.
     */
    public void run () {
	try {
	    final InetAddress ia = dnsHandler.getInetAddress (url);
	    ial.lookupDone (ia);
	} catch (final UnknownHostException e) {
	    ial.unknownHost (e);
	}
    }
}

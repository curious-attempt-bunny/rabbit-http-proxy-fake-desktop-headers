package rabbit.io;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import rabbit.nio.ConnectHandler;
import rabbit.nio.NioHandler;
import rabbit.util.Counter;

/** A class to handle a connection to the Internet.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class WebConnection implements Closeable {
    private int id;
    private Address address;
    private Counter counter;
    private SocketChannel channel;
    private long releasedAt = -1;
    private boolean keepalive = true;
    private boolean mayPipeline = false;

    private static AtomicInteger idCounter = new AtomicInteger (0);

    /** Create a new WebConnection to the given InetAddress and port.
     * @param address the computer to connect to.
     * @param counter the Counter to used to collect statistics
     */
    public WebConnection (Address address, Counter counter) {
	this.id = idCounter.getAndIncrement ();
	this.address = address;
	this.counter = counter;
	counter.inc ("WebConnections created");
    }

    @Override public String toString () {
	int port = channel != null ? channel.socket ().getLocalPort () : -1;
	return "WebConnection(id: " + id +
	    ", address: "  + address +
	    ", keepalive: " + keepalive +
	    ", releasedAt: " + releasedAt + 
	    ", local port: " + port + ")";
    }

    public Address getAddress () {
	return address;
    }

    public SocketChannel getChannel () {
	return channel;
    }

    public void close () throws IOException {
	counter.inc ("WebConnections closed");
	channel.close ();
    }

    public void connect (NioHandler nioHandler, WebConnectionListener wcl)
	throws IOException {
	// if we are a keepalive connection then just say so..
	if (channel != null && channel.isConnected ()) {
	    wcl.connectionEstablished (this);
	} else {
	    // ok, open the connection....
	    channel = SocketChannel.open ();
	    channel.configureBlocking (false);
	    SocketAddress addr =
		new InetSocketAddress (address.getInetAddress (),
				       address.getPort ());
	    boolean connected = channel.connect (addr);
	    if (connected) {
		channel.socket ().setTcpNoDelay (true);
		wcl.connectionEstablished (this);
	    } else {
		new ConnectListener (wcl).waitForConnection (nioHandler);
	    }
	}
    }

    private class ConnectListener implements ConnectHandler {
	private WebConnectionListener wcl;
	private Long timeout;

	public ConnectListener (WebConnectionListener wcl) {
	    this.wcl = wcl;
	}

	public void waitForConnection (NioHandler nioHandler) throws IOException {
	    timeout = nioHandler.getDefaultTimeout ();
	    nioHandler.waitForConnect (channel, this);
	}

	public void closed () {
	    wcl.failed (new IOException ("channel closed before connect"));
	}

	public void timeout () {
	    wcl.timeout ();
	}

	public boolean useSeparateThread () {
	    return false;
	}

	public String getDescription () {
	    return "WebConnection$ConnectListener: address: " + address;
	}

	public Long getTimeout () {
	    return timeout;
	}

	public void connect () {
	    try {
		channel.finishConnect ();
		channel.socket ().setTcpNoDelay (true);
		wcl.connectionEstablished (WebConnection.this);
	    } catch (IOException e) {
		wcl.failed (e);
	    }
	}

	@Override public String toString () {
	    return getClass ().getSimpleName () + "{" + address + "}@" +
		Integer.toString (hashCode (), 16);
	}
    }

    /** Set the keepalive value for this WebConnection,
     *  Can only be turned off.
     * @param b the new keepalive value.
     */
    public void setKeepalive (boolean b) {
	keepalive &= b;
    }

    /** Get the keepalive value of this WebConnection.
     * @return true if this WebConnection may be reused.
     */
    public boolean getKeepalive () {
	return keepalive;
    }

    /** Mark this WebConnection as released at current time.
     */
    public void setReleased () {
	releasedAt = System.currentTimeMillis ();
    }

    /** Get the time that this WebConnection was released.
     */
    public long getReleasedAt () {
	return releasedAt;
    }

    /** Mark this WebConnection for pipelining.
     */
    public void setMayPipeline (boolean b) {
	mayPipeline = b;
    }

    /** Check if this WebConnection may be used for pipelining.
     */
    public boolean mayPipeline () {
	return mayPipeline;
    }
}

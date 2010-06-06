package rabbit.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.khelekore.rnio.NioHandler;
import org.khelekore.rnio.StatisticsHolder;
import org.khelekore.rnio.impl.BasicStatisticsHolder;
import org.khelekore.rnio.impl.MultiSelectorNioHandler;
import rabbit.cache.Cache;
import rabbit.cache.NCache;
import rabbit.dns.DNSHandler;
import rabbit.dns.DNSJavaHandler;
import rabbit.dns.DNSSunHandler;
import rabbit.handler.HandlerFactory;
import rabbit.http.HttpDateParser;
import rabbit.http.HttpHeader;
import rabbit.httpio.Acceptor;
import rabbit.httpio.AcceptorListener;
import rabbit.httpio.ProxiedProxyChain;
import rabbit.httpio.SimpleProxyChain;
import rabbit.io.BufferHandler;
import rabbit.io.CachingBufferHandler;
import rabbit.io.ConnectionHandler;
import rabbit.io.ProxyChain;
import rabbit.io.ProxyChainFactory;
import rabbit.io.WebConnection;
import rabbit.io.WebConnectionListener;
import rabbit.util.Config;
import rabbit.util.Counter;
import rabbit.util.SProperties;

/** A filtering and caching http proxy.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HttpProxy {

    /** Current version */
    public static final String VERSION = "RabbIT proxy version 4.7";

    /** The current config of this proxy. */
    private Config config;

    /** The time this proxy was started. Time in millis. */
    private long started;

    /** The identity of this server. */
    private String serverIdentity = VERSION;

    /** The logger of this proxy. */
    private final Logger logger =  Logger.getLogger (getClass ().getName ());

    /** The access logger of the proxy */
    private final ProxyLogger accessLogger = new ProxyLogger ();

    /** The id sequence for acceptors. */
    private static int acceptorId = 0;

    /** The dns handler */
    private DNSHandler dnsHandler;

    /** The socket access controller. */
    private SocketAccessController socketAccessController;

    /** The http header filterer. */
    private HttpHeaderFilterer httpHeaderFilterer;

    /** The connection handler */
    private ConnectionHandler conhandler;

    /** The local adress of the proxy. */
    private InetAddress localhost;

    /** The port the proxy is using. */
    private int port = -1;

    /** The proxy chain we are using */
    private ProxyChain proxyChain;

    /** The serversocket the proxy is using. */
    private ServerSocketChannel ssc = null;

    private NioHandler nioHandler;

    /** The buffer handlers. */
    private BufferHandler bufferHandler = new CachingBufferHandler ();

    /** If this proxy is using strict http parsing. */
    private boolean strictHttp = true;

    /** Maximum number of concurrent connections */
    private int maxConnections = 50;

    /** The counter of events. */
    private Counter counter = new Counter ();

    /** The cache-handler */
    private NCache<HttpHeader, HttpHeader> cache;

    /** Are we allowed to proxy ssl? */
    protected boolean proxySSL = false;
    /** The List of acceptable ssl-ports. */
    protected List<Integer> sslports = null;

    /** The handler factory handler. */
    private HandlerFactoryHandler handlerFactoryHandler;

    /** All the currently active connections. */
    private List<Connection> connections = new ArrayList<Connection> ();

    /** The total traffic in and out of this proxy. */
    private TrafficLoggerHandler tlh = new TrafficLoggerHandler ();

    private HttpGeneratorFactory hgf;

    public HttpProxy () throws UnknownHostException {
	localhost = InetAddress.getLocalHost ();
    }

    /** Set the config file to use for this proxy.
     * @param conf the name of the file to use for proxy configuration.
     */
    public void setConfig (String conf) throws IOException {
	setConfig (new Config (conf));
    }

    private void setupLogging () {
	SProperties logProps = config.getProperties ("logging");
	try {
	    accessLogger.setup (logProps);
	} catch (IOException e) {
	    logger.log (Level.SEVERE,
			"Failed to configure logging",
			e);
	}
    }

    private void setupDateParsing () {
	HttpDateParser.setOffset (getOffset ());
    }

    private void setupDNSHandler () {
	/* DNSJava have problems with international versions of windows.
	 * so we default to the default dns handler.
	 */
	String osName = System.getProperty ("os.name");
	if (osName.toLowerCase ().indexOf ("windows") > -1) {
	    logger.warning ("This seems like a windows system, " +
			    "will use default sun handler for DNS");
	    dnsHandler = new DNSSunHandler ();
	} else {
	    String dnsHandlerClass =
		config.getProperty (getClass ().getName (), "dnsHandler",
				    DNSJavaHandler.class.getName ());
	    try {
		Class<? extends DNSHandler> clz =
		    Class.forName (dnsHandlerClass).asSubclass (DNSHandler.class);
		dnsHandler = clz.newInstance ();
		dnsHandler.setup (config.getProperties ("dns"));
	    } catch (Exception e) {
		logger.warning ("Unable to create and setup dns handler: " + e +
				", will try to use default instead.");
		dnsHandler = new DNSJavaHandler ();
		dnsHandler.setup (config.getProperties ("dns"));
	    }
	}
    }

    private void setupNioHandler () {
	String section = getClass ().getName ();
	int cpus = Runtime.getRuntime ().availableProcessors ();
	int threads = getInt (section, "num_selector_threads", cpus);
	ExecutorService es = Executors.newCachedThreadPool ();
	StatisticsHolder sh = new BasicStatisticsHolder ();
	Long timeout = Long.valueOf (15000);
	try {
	    nioHandler =
		new MultiSelectorNioHandler (es, sh, threads, timeout);
	} catch (IOException e) {
	    logger.log (Level.SEVERE,
			"Failed to create the NioHandler",
			e);
	    stop ();
	}
    }

    private ProxyChain setupProxyChainFromFactory (String pcf) {
	try {
	    Class<? extends ProxyChainFactory> clz =
		Class.forName (pcf).asSubclass (ProxyChainFactory.class);
	    ProxyChainFactory factory = clz.newInstance ();
	    SProperties props = config.getProperties (pcf);
	    return 
		factory.getProxyChain (props, nioHandler, dnsHandler, logger);
	} catch (Exception e) {
	    logger.log (Level.WARNING,
			"Unable to create the proxy chain " +
			"will fall back to the default one.",
			e);
	}
	return null;
    }

    /* TODO: remove this method, only kept for backwards compability. */
    private ProxyChain setupProxiedProxyChain (String pname, String pport,
					       String pauth) {
	try {
	    InetAddress proxy = dnsHandler.getInetAddress (pname);
	    try {
		int port = Integer.parseInt (pport);
		return new ProxiedProxyChain (proxy, port, pauth);
	    } catch (NumberFormatException e) {
		logger.severe ("Strange proxyport: '" + pport +
			       "', will not chain");
	    }
	} catch (UnknownHostException e) {
	    logger.severe ("Unknown proxyhost: '" + pname +
			   "', will not chain");
	}
	return null;
    }

    /** Configure the chained proxy rabbit is using (if any).
     */
    private void setupProxyConnection () {
	String sec = getClass ().getName ();
	String pcf =
	    config.getProperty (sec, "proxy_chain_factory", "").trim ();
	String pname = config.getProperty (sec, "proxyhost", "").trim ();
	String pport = config.getProperty (sec, "proxyport", "").trim ();
	String pauth = config.getProperty (sec, "proxyauth");

	if (!"".equals(pcf)) {
	    proxyChain = setupProxyChainFromFactory (pcf);
	} else if (!pname.equals ("") && !pport.equals ("")) {
	    proxyChain = setupProxiedProxyChain (pname, pport, pauth);
	}
	if (proxyChain == null)
	    proxyChain = new SimpleProxyChain (nioHandler, dnsHandler);
    }

    private void setupCache () {
	SProperties props =
	    config.getProperties (NCache.class.getName ());
	HttpHeaderFileHandler hhfh = new HttpHeaderFileHandler ();
	try {
	    cache = new NCache<HttpHeader, HttpHeader> (props, hhfh, hhfh);
	    cache.startCleaner ();
	} catch (IOException e) {
	    logger.log (Level.SEVERE,
			"Failed to setup cache",
			e);
	}
    }

    /** Configure the SSL support RabbIT should have.
     */
    private void setupSSLSupport () {
	String ssl = config.getProperty ("sslhandler", "allowSSL", "no");
	ssl = ssl.trim ();
	if (ssl.equals ("no")) {
	    proxySSL = false;
	} else if (ssl.equals ("yes")) {
	    proxySSL = true;
	    sslports = null;
	} else {
	    proxySSL = true;
	    // ok, try to get the portnumbers.
	    sslports = new ArrayList<Integer> ();
	    StringTokenizer st = new StringTokenizer (ssl, ",");
	    while (st.hasMoreTokens ()) {
		String s = null;
		try {
		    Integer port = new Integer (s = st.nextToken ());
		    sslports.add (port);
		} catch (NumberFormatException e) {
		    logger.warning ("bad number: '" + s +
				    "' for ssl port, ignoring.");
		}
	    }
	}
    }

    public void setStrictHttp (boolean b) {
	this.strictHttp = b;
    }

    public boolean getStrictHttp () {
	return strictHttp;
    }

    /** Configure the maximum number of simultanious connections we handle
     */
    private void setupMaxConnections () {
	String mc = config.getProperty (getClass ().getName (),
					"maxconnections", "500").trim ();
	try {
	    maxConnections = Integer.parseInt (mc);
	} catch (NumberFormatException e) {
	    logger.warning ("bad number for maxconnections: '" +
			    mc + "', using old value: " + maxConnections);
	}
    }

    private void setupConnectionHandler () {
	if (nioHandler == null) {
	    logger.info ("nioHandler == null " + this);
	    return;
	}
	conhandler = new ConnectionHandler (counter, proxyChain, nioHandler);
	String section = conhandler.getClass ().getName ();
	conhandler.setup (config.getProperties (section));
    }

    private void setupHttpGeneratorFactory () {
	String def = StandardHttpGeneratorFactory.class.getName ();
	String hgfClass = config.getProperty (getClass ().getName (),
					      "http_generator_factory", def);
	try {
	    Class<? extends HttpGeneratorFactory> clz =
		Class.forName (hgfClass).asSubclass (HttpGeneratorFactory.class);
	    hgf = clz.newInstance ();
	} catch (Exception e) {
	    logger.log (Level.WARNING,
			"Unable to create the http generator " +
			"factory, will fall back to the default one.",
			e);
	    hgf = new StandardHttpGeneratorFactory ();
	}
	String section = hgf.getClass ().getName ();
	hgf.setup (config.getProperties (section));
    }

    private void setConfig (Config config) {
	this.config = config;
	setupLogging ();
	setupDateParsing ();
	setupDNSHandler ();
	setupNioHandler ();
	setupProxyConnection ();
	String cn = getClass ().getName ();
	serverIdentity = config.getProperty (cn, "serverIdentity", VERSION);
	String strictHttp = config.getProperty (cn, "StrictHTTP", "true");
	setStrictHttp (strictHttp.equals ("true"));
	setupMaxConnections ();
	setupCache ();
	setupSSLSupport ();
	loadClasses ();
	openSocket ();
	setupConnectionHandler ();
	setupHttpGeneratorFactory ();
	logger.info (VERSION + ": Configuration loaded: ready for action.");
    }

    private int getInt (String section, String key, int defaultValue) {
	String defVal = Integer.toString (defaultValue);
	String configValue = config.getProperty (section, key, defVal).trim ();
	return Integer.parseInt (configValue);
    }

    /** Open a socket on the specified port
     *  also make the proxy continue accepting connections.
     */
    private void openSocket () {
	String section = getClass ().getName ();
	int tport = getInt (section, "port", 9666);

	String bindIP = config.getProperty (section, "listen_ip");
	if (tport != port) {
	    try {
		closeSocket ();
		port = tport;
		ssc = ServerSocketChannel.open ();
		ssc.configureBlocking (false);
		if (bindIP == null) {
		    ssc.socket ().bind (new InetSocketAddress (port));
		} else {
		    InetAddress ia = InetAddress.getByName (bindIP);
		    logger.info ("listening on inetaddress: " + ia +
				 ":" + port +
				 " on inet address: " + ia);
		    ssc.socket ().bind (new InetSocketAddress (ia, port));
		}
		AcceptorListener listener =
		    new ProxyConnectionAcceptor (acceptorId++, this);
		Acceptor acceptor = new Acceptor (ssc, nioHandler, listener);
		acceptor.register ();
	    } catch (IOException e) {
		logger.log (Level.SEVERE,
			    "Failed to open serversocket on port " + port,
			    e);
		stop ();
	    }
	}
    }

    /** Closes the serversocket and makes the proxy stop listening for
     *	connections.
     */
    private void closeSocket () {
	try {
	    port = -1;
	    if (ssc != null) {
		ssc.close ();
		ssc = null;
	    }
	} catch (IOException e) {
	    logger.severe ("Failed to close serversocket on port " + port);
	    stop ();
	}
    }

    private void closeNioHandler () {
	if (nioHandler != null)
	    nioHandler.shutdown ();
    }

    /** Make sure all filters and handlers are available
     */
    private void loadClasses () {
	SProperties hProps = config.getProperties ("Handlers");
	SProperties chProps = config.getProperties ("CacheHandlers");
	handlerFactoryHandler =
	    new HandlerFactoryHandler (hProps, chProps, config);

	String filters = config.getProperty ("Filters", "accessfilters","");
	socketAccessController =
	    new SocketAccessController (filters, config);

	String in = config.getProperty ("Filters", "httpinfilters","");
	String out = config.getProperty ("Filters", "httpoutfilters","");
	httpHeaderFilterer =
	    new HttpHeaderFilterer (in, out, config, this);
    }


    /** Run the proxy in a separate thread. */
    public void start () {
	started = System.currentTimeMillis ();
	nioHandler.start ();
    }

    /** Run the proxy in a separate thread. */
    public void stop () {
	logger.severe ("HttpProxy.stop() called, shutting down");
	synchronized (this) {
	    closeSocket ();
	    // TODO: wait for remaining connections.
	    // TODO: as it is now, it will just close connections in the middle.
	    closeNioHandler ();
	    cache.flush ();
	    cache.stop ();
	}
    }

    public NioHandler getNioHandler () {
	return nioHandler;
    }

    public Cache<HttpHeader, HttpHeader> getCache () {
	return cache;
    }

    public long getOffset () {
	return accessLogger.getOffset ();
    }

    public long getStartTime () {
	return started;
    }

    ConnectionLogger getConnectionLogger () {
	return accessLogger;
    }

    ServerSocketChannel getServerSocketChannel () {
	return ssc;
    }

    public Counter getCounter () {
	return counter;
    }

    SocketAccessController getSocketAccessController () {
	return socketAccessController;
    }

    HttpHeaderFilterer getHttpHeaderFilterer () {
	return httpHeaderFilterer;
    }

    /** Get the configuration of the proxy. */
    public Config getConfig () {
	return config;
    }

    HandlerFactory getHandlerFactory (String mime) {
	return handlerFactoryHandler.getHandlerFactory (mime);
    }

    HandlerFactory getCacheHandlerFactory (String mime) {
	return handlerFactoryHandler.getCacheHandlerFactory (mime);
    }

    public String getVersion () {
	return VERSION;
    }

    public String getServerIdentity () {
	return serverIdentity;
    }

    /** Get the local host.
     * @return the InetAddress of the host the proxy is running on.
     */
    public InetAddress getHost () {
	return localhost;
    }

    /** Get the port this proxy is using.
     * @return the port number the proxy is listening on.
     */
    public int getPort () {
	return port;
    }

    public ProxyChain getProxyChain () {
	return proxyChain;
    }

    /** Try hard to check if the given address matches the proxy.
     *  Will use the localhost name and all ip addresses.
     */
    public boolean isSelf (String uhost, int urlport) {
	if (urlport == getPort ()) {
	    String proxyhost = getHost ().getHostName ();
	    if (uhost.equalsIgnoreCase (proxyhost))
		return true;
	    try {
		Enumeration<NetworkInterface> e =
		    NetworkInterface.getNetworkInterfaces();
		while (e.hasMoreElements ()) {
		    NetworkInterface ni = e.nextElement ();
		    Enumeration<InetAddress> ei = ni.getInetAddresses ();
		    while (ei.hasMoreElements ()) {
			InetAddress ia = ei.nextElement ();
			if (ia.getHostAddress ().equalsIgnoreCase (uhost))
			    return true;
			if (ia.isLoopbackAddress () &&
			    ia.getHostName ().equalsIgnoreCase (uhost))
			    return true;
		    }
		}
	    } catch (SocketException e) {
		logger.log (Level.WARNING,
			    "Failed to get network interfaces", e);
	    }
	}
	return false;
    }

    /** Get a WebConnection.
     * @param header the http header to get the host and port from
     * @param wcl the listener that wants to get the connection.
     */
    public void getWebConnection (HttpHeader header,
				  WebConnectionListener wcl) {
	conhandler.getConnection (header, wcl);
    }

    /** Release a WebConnection so that it may be reused if possible.
     * @param wc the WebConnection to release.
     */
    public void releaseWebConnection (WebConnection wc) {
	conhandler.releaseConnection (wc);
    }

    /** Mark a WebConnection for pipelining.
     * @param wc the WebConnection to mark.
     */
    public void markForPipelining (WebConnection wc) {
	conhandler.markForPipelining (wc);
    }

    /** Add a current connection
     * @param con the connection
     */
    public void addCurrentConnection (Connection con) {
	connections.add (con);
    }

    /** Remove a current connection.
     * @param con the connection
     */
    public void removeCurrentConnection (Connection con) {
	connections.remove (con);
    }

    /** Get the connection handler.
     */
    public ConnectionHandler getConnectionHandler () {
	return conhandler;
    }

    /** Get all the current connections
     */
    public List<Connection> getCurrentConnections () {
	return Collections.unmodifiableList (connections);
    }

    /** Update the currently transferred traffic statistics.
     */
    protected void updateTrafficLog (TrafficLoggerHandler tlh) {
	synchronized (this.tlh) {
	    tlh.addTo (this.tlh);
	}
    }

    /** Get the currently transferred traffic statistics.
     */
    public TrafficLoggerHandler getTrafficLoggerHandler () {
	return tlh;
    }

    protected BufferHandler getBufferHandler () {
	return bufferHandler;
    }

    public HttpGeneratorFactory getHttpGeneratorFactory () {
	return hgf;
    }
}

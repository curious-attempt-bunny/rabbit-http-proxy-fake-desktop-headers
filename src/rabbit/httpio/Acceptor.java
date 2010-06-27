package rabbit.httpio;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;
import org.khelekore.rnio.AcceptHandler;
import org.khelekore.rnio.NioHandler;
import org.khelekore.rnio.impl.Closer;

/** A standard acceptor.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class Acceptor implements AcceptHandler {
    private final ServerSocketChannel ssc;
    private final NioHandler nioHandler;
    private final AcceptorListener listener;

    private final Logger logger = Logger.getLogger (getClass ().getName ());

    public Acceptor (ServerSocketChannel ssc, 
		     NioHandler nioHandler,
		     AcceptorListener listener) {
	this.ssc = ssc;
	this.nioHandler = nioHandler;
	this.listener = listener;
    }

    public void closed () {
	if (ssc.isOpen ())
	    Closer.close (ssc, logger);
    }

    /** Handle timeout, since an acceptor should not get timeouts an 
     *  exception will be thrown.
     */ 
    public void timeout () {
	throw new IllegalStateException ("Acceptor should not get timeout");
    }
    
    /** Acceptor runs in the selector thread.
     */ 
    public boolean useSeparateThread () {
	return false;
    }

    public String getDescription () {
	return "Acceptor: ssc: " + ssc;
    }

    public Long getTimeout () {
	return null;
    }

    /** Accept a SocketChannel.
     */ 
    public void accept () {
	try {
	    SocketChannel sc = ssc.accept ();
	    sc.configureBlocking (false);
	    listener.connectionAccepted (sc);
	    register ();
	} catch (IOException e) {
	    throw new RuntimeException ("Got some IOException", e);
	}
    }

    /** Register OP_ACCEPT with the selector. 
     */ 
    public void register ()  {
	nioHandler.waitForAccept (ssc, this);
    }
}

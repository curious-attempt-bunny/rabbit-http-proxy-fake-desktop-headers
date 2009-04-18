package rabbit.httpio;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;
import rabbit.io.BufferHandle;
import rabbit.nio.NioHandler;
import rabbit.nio.SocketChannelHandler;

/** A base class for socket handlers.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public abstract class BaseSocketHandler implements SocketChannelHandler {
    /** The client channel. */
    protected SocketChannel channel; 
    
    /** The nio handler we are using. */
    protected NioHandler nioHandler;

    /** The logger to use. */
    private final Logger logger = 
	Logger.getLogger (Logger.class.getName ());
    
    /** The buffer handle. */
    protected BufferHandle bh;
    
    public BaseSocketHandler (SocketChannel channel, BufferHandle bh, 
			      NioHandler nioHandler) {
	this.channel = channel;
	this.bh = bh;
	this.nioHandler = nioHandler;
    }

    protected ByteBuffer getBuffer () {
	return bh.getBuffer ();
    }

    protected void growBuffer () {
	bh.growBuffer ();
    }

    protected void releaseBuffer () {
	bh.possiblyFlush ();
    }

    /** Does nothing by default */
    public void closed () {
    }

    /** Does nothing by default */
    public void timeout () {
    }

    public boolean useSeparateThread () {
	return false;
    }

    public String getDescription () {
	return getClass ().getName () + ":" + channel;
    }

    public Long getTimeout () {
	// TODO: implement 
	return null;
    }

    protected Logger getLogger () {
	return 	logger;
    }

    protected void closeDown () {
	releaseBuffer ();
	nioHandler.close (channel);
	clear ();
    }

    public void clear () {
	nioHandler = null;
	channel = null;
    }
}

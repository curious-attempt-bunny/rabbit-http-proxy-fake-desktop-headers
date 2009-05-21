package rabbit.httpio;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;
import rabbit.io.BufferHandle;
import rabbit.nio.NioHandler;
import rabbit.nio.ReadHandler;
import rabbit.nio.SocketChannelHandler;
import rabbit.nio.WriteHandler;

/** A base class for socket handlers.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public abstract class BaseSocketHandler implements SocketChannelHandler {
    /** The client channel. */
    private final SocketChannel channel; 
    
    /** The nio handler we are using. */
    private final NioHandler nioHandler;

    /** The logger to use. */
    private final Logger logger = Logger.getLogger (getClass ().getName ());
    
    /** The buffer handle. */
    private final BufferHandle bh;
    
    /** The timeout value set by the previous channel registration */
    private Long timeout;

    public BaseSocketHandler (SocketChannel channel, BufferHandle bh, 
			      NioHandler nioHandler) {
	this.channel = channel;
	this.bh = bh;
	this.nioHandler = nioHandler;
    }

    protected ByteBuffer getBuffer () {
	return bh.getBuffer ();
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

    /** Runs on the selector thread by default */
    public boolean useSeparateThread () {
	return false;
    }

    public String getDescription () {
	return getClass ().getName () + ":" + channel;
    }

    public Long getTimeout () {
	return timeout;
    }

    protected Logger getLogger () {
	return 	logger;
    }

    protected void closeDown () {
	releaseBuffer ();
	nioHandler.close (channel);
    }

    public SocketChannel getChannel () {
	return channel;
    }

    public BufferHandle getBufferHandle () {
	return bh;
    }

    public void waitForRead (ReadHandler rh) {
	this.timeout = nioHandler.getDefaultTimeout ();
	nioHandler.waitForRead (channel, rh);
    }

    public void waitForWrite (WriteHandler rh) {
	this.timeout = nioHandler.getDefaultTimeout ();
	nioHandler.waitForWrite (channel, rh);
    }
}

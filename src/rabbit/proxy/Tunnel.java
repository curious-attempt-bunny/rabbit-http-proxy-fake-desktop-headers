package rabbit.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import rabbit.io.BufferHandle;
import rabbit.io.Closer;
import rabbit.nio.NioHandler;
import rabbit.nio.ReadHandler;
import rabbit.nio.WriteHandler;
import rabbit.util.TrafficLogger;

/** A handler that just tunnels data.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class Tunnel {
    private final NioHandler nioHandler;
    private final Logger logger = Logger.getLogger (getClass ().getName ());
    private final OneWayTunnel fromToTo;
    private final OneWayTunnel toToFrom;
    private final TunnelDoneListener listener;

    public Tunnel (NioHandler nioHandler, SocketChannel from, 
		   BufferHandle fromHandle,
		   TrafficLogger fromLogger, 
		   SocketChannel to, BufferHandle toHandle, 
		   TrafficLogger toLogger,
		   TunnelDoneListener listener) 
	throws IOException {
	if (logger.isLoggable (Level.FINEST))
	    logger.finest ("Tunnel created from: " + from + " to: " + to);
	this.nioHandler = nioHandler;
	fromToTo = new OneWayTunnel (from, to, fromHandle, fromLogger);
	toToFrom = new OneWayTunnel (to, from, toHandle, toLogger);
	this.listener = listener;
    }

    public void start () {
	if (logger.isLoggable (Level.FINEST))
	    logger.finest ("Tunnel started");
	fromToTo.start ();
	toToFrom.start ();
    }

    private class OneWayTunnel implements ReadHandler, WriteHandler {
	private final SocketChannel from;
	private final SocketChannel to;
	private final BufferHandle bh;
	private final TrafficLogger tl;
	
	public OneWayTunnel (SocketChannel from, SocketChannel to, 
			     BufferHandle bh, TrafficLogger tl) {
	    this.from = from;
	    this.to = to;
	    this.bh = bh;
	    this.tl = tl;
	}

	public void start () {
	    if (logger.isLoggable (Level.FINEST))
		logger.finest ("OneWayTunnel started: bh.isEmpty: " + 
			       bh.isEmpty ());
	    if (bh.isEmpty ())
		waitForRead ();
	    else 
		writeData ();
	}

	private void waitForRead () {
	    nioHandler.waitForRead (from, this);
	}

	private void waitForWrite () {
	    bh.possiblyFlush ();
	    nioHandler.waitForWrite (to, this);
	}

	public void unregister () {
	    nioHandler.cancel (from, this);
	    nioHandler.cancel (to, this);

	    // clear buffer and return it.
	    ByteBuffer buf = bh.getBuffer ();
	    buf.position (buf.limit ());
	    bh.possiblyFlush ();
	}

	private void writeData () {
	    try {
		if (!to.isOpen ()) {
		    logger.warning ("Tunnel to is closed, not writing data");
		    closeDown ();
		    return;
		}
		ByteBuffer buf = bh.getBuffer ();
		if (buf.hasRemaining ()) {
		    int written = 0;
		    do {
			written = to.write (buf);
			if (logger.isLoggable (Level.FINEST))
			    logger.finest ("OneWayTunnel wrote: " + written);
			tl.write (written);
		    } while (written > 0 && buf.hasRemaining ());
		}
		
		if (buf.hasRemaining ())
		    waitForWrite ();
		else
		    waitForRead ();
	    } catch (IOException e) {
		logger.warning ("Got exception writing to tunnel: " + e);
		closeDown ();
	    }
	}

	public void closed () {
	    logger.info ("Tunnel closed");
	    closeDown ();
	}
	
	public void timeout () {
	    logger.warning ("Tunnel got timeout");
	    closeDown ();
	}

	public boolean useSeparateThread () {
	    return false;
	}

	public String getDescription () {
	    return "Tunnel part from: " + from + " to: " + to;
	}

	public Long getTimeout () {
	    return null;
	}
	
	public void read () {
	    try {
		if (!from.isOpen ()) {
		    logger.warning ("Tunnel to is closed, not reading data");
		    return;
		}
		ByteBuffer buffer = bh.getBuffer ();
		buffer.clear ();
		int read = from.read (buffer);
		if (logger.isLoggable (Level.FINEST))
		    logger.finest ("OneWayTunnel read: " + read);
		if (read == -1) {
		    buffer.position (buffer.limit ());
		    closeDown ();
		} else { 
		    buffer.flip ();
		    tl.read (read);
		    writeData ();
		}
	    } catch (IOException e) {
		logger.warning ("Got exception reading from tunnel: " + e);
	    }
	}

	public void write () {
	    writeData ();
	}
    }

    private void closeDown () {
	fromToTo.unregister ();
	toToFrom.unregister ();
	// we do not want to close the channels, 
	// it is up to the listener to do that.
	if (listener != null) {
	    listener.tunnelClosed ();
	} else {
	    // hmm? no listeners, then close down
	    Closer.close (fromToTo.from, logger);
	    Closer.close (toToFrom.from, logger);
	}
    }
}

package rabbit.nio;

import java.nio.channels.SelectionKey;
import java.util.concurrent.ExecutorService;

/** The handler of channel operations. 
 */
class ChannelOpsHandler {
    private ReadHandler readHandler;
    private WriteHandler writeHandler;
    private AcceptHandler acceptHandler;
    private ConnectHandler connectHandler;

    @Override public String toString () {
	return getClass ().getSimpleName () + "{" + 
	    "r: " + readHandler + 
	    ", w: " + writeHandler + 
	    ", a: " + acceptHandler + 
	    ", c: " + connectHandler + "}";
    }

    public int getInterestOps () {
	int ret = 0;
	if (readHandler != null)
	    ret |= SelectionKey.OP_READ;
	if (writeHandler != null)
	    ret |= SelectionKey.OP_WRITE;
	if (acceptHandler != null)
	    ret |= SelectionKey.OP_ACCEPT;
	if (connectHandler != null)
	    ret |= SelectionKey.OP_CONNECT;
	return ret;
    }

    private void checkNullHandler (SocketChannelHandler handler, 
				   SocketChannelHandler newHandler,
				   String type) {
	if (handler != null) {
	    String msg = "Trying to overwrite the existing " + type + ": " + 
		handler + ", new " + type + ": " + readHandler;
	    throw new IllegalStateException (msg);
	}
    }

    public void setReadHandler (ReadHandler readHandler) {
	checkNullHandler (this.readHandler, readHandler, "readHandler");
	this.readHandler = readHandler;
    }

    public void setWriteHandler (WriteHandler writeHandler) {
	checkNullHandler (this.writeHandler, writeHandler, "writeHandler");
	this.writeHandler = writeHandler;
    }

    public void setAcceptHandler (AcceptHandler acceptHandler) {
	checkNullHandler (this.acceptHandler, acceptHandler, "acceptHandler");
	this.acceptHandler = acceptHandler;
    }

    public void setConnectHandler (ConnectHandler connectHandler) {
	checkNullHandler (this.connectHandler, connectHandler, 
			  "connectHandler");
	this.connectHandler = connectHandler;
    }

    private void handleRead (ExecutorService executorService, 
			     final ReadHandler rh) {
	if (rh.useSeparateThread ()) {
	    executorService.execute (new Runnable () {
		    public void run () {
			rh.read ();
		    }
		});
	} else {
	    rh.read ();	    
	}
    }

    private void handleWrite (ExecutorService executorService, 
			      final WriteHandler wh) {
	if (wh.useSeparateThread ()) {
	    executorService.execute (new Runnable () {
		    public void run () {
			wh.write ();
		    }
		});
	} else {
	    wh.write (); 
	}
    }

    private void handleAccept (ExecutorService executorService, 
			       final AcceptHandler ah) {
	if (ah.useSeparateThread ()) {
	    executorService.execute (new Runnable () {
		    public void run () {
			ah.accept ();
		    }
		});
	} else {
	    ah.accept ();	    
	}
    }

    private void handleConnect (ExecutorService executorService, 
				final ConnectHandler ch) {
	if (ch.useSeparateThread ()) {
	    executorService.execute (new Runnable () {
		    public void run () {
			ch.connect ();
		    }
		});
	} else {
	    ch.connect ();	    
	}
    }

    public void handle (ExecutorService executorService, SelectionKey sk) {
	sk.interestOps (0);
	ReadHandler rh = readHandler;
	WriteHandler wh = writeHandler;
	AcceptHandler ah = acceptHandler;
	ConnectHandler ch = connectHandler;
	readHandler = null;
	writeHandler = null;
	acceptHandler = null;
	connectHandler = null;
	
	if (sk.isReadable ())
	    handleRead (executorService, rh);
	else
	    setReadHandler (rh);
	if (sk.isValid () && sk.isWritable ())
	    handleWrite (executorService, wh);
	else 
	    setWriteHandler (wh);
	if (sk.isValid () && sk.isAcceptable ())
	    handleAccept (executorService, ah);
	else 
	    setAcceptHandler (ah);
	if (sk.isValid () && sk.isConnectable ())
	    handleConnect (executorService, ch);
	else 
	    setConnectHandler (ch);
    }

    private boolean doTimeout (long now, SocketChannelHandler sch) {
	if (sch == null)
	    return false;
	Long t = sch.getTimeout ();
	if (t == null)
	    return false;
	boolean ret = t.longValue () < now;
	if (ret)
	    sch.timeout ();
	return ret;
    }

    public void doTimeouts (long now) {
	if (doTimeout (now, readHandler))
	    readHandler = null;
	if (doTimeout (now, writeHandler))
	    writeHandler = null;
	if (doTimeout (now, acceptHandler))
	    acceptHandler = null;
	if (doTimeout (now, connectHandler))
	    connectHandler = null;
    }

    private Long minTimeout (Long t, SocketChannelHandler sch) {
	if (sch == null)
	    return t;
	Long t2 = sch.getTimeout ();
	if (t == null)
	    return t2;
	if (t2 == null)
	    return t;
	return t2.longValue () < t.longValue () ? t2 : t;
    }

    public Long getMinimumTimeout () {
	Long t = readHandler != null ? readHandler.getTimeout () : null;
	t = minTimeout (t, writeHandler);
	t = minTimeout (t, acceptHandler);
	t = minTimeout (t, connectHandler);
	return t;
    }

    private void closedIfSet (SocketChannelHandler sch) {
	if (sch != null)
	    sch.closed ();
    }

    public void closed () {
	closedIfSet (readHandler);
	closedIfSet (writeHandler);
	closedIfSet (acceptHandler);
	closedIfSet (connectHandler);
    }
}

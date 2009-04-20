package rabbit.httpio;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import rabbit.io.BufferHandle;
import rabbit.nio.NioHandler;
import rabbit.nio.WriteHandler;
import rabbit.util.TrafficLogger;

/** A handler that writes data blocks.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class BlockSender extends BaseSocketHandler implements WriteHandler {
    private ByteBuffer chunkBuffer;
    private ByteBuffer end;
    private ByteBuffer[] buffers;
    private final TrafficLogger tl;
    private final BlockSentListener sender;
    
    public BlockSender (SocketChannel channel, NioHandler nioHandler, 
			TrafficLogger tl, 
			BufferHandle bufHandle, boolean chunking, 
			BlockSentListener sender) 
	throws IOException {
	super (channel, bufHandle, nioHandler);
	this.tl = tl;
	ByteBuffer buffer = bufHandle.getBuffer ();
	if (chunking) {
	    int len = buffer.remaining ();
	    String s = Long.toHexString (len) + "\r\n";
	    try {
		chunkBuffer = ByteBuffer.wrap (s.getBytes ("ASCII"));
	    } catch (UnsupportedEncodingException e) {
		getLogger ().log (Level.WARNING, 
				  "BlockSender: ASCII not found!", 
				  e);
	    }
	    end = ByteBuffer.wrap (new byte[] {'\r', '\n'});
	    buffers = new ByteBuffer[]{chunkBuffer, buffer, end};
	} else {
	    buffers = new ByteBuffer[]{buffer};
	    end = buffer;
	}
	this.sender = sender;
    }

    @Override public String getDescription () {
	StringBuilder sb = 
	    new StringBuilder ("BlockSender: buffers: " + buffers.length);
	for (int i = 0; i < buffers.length; i++) {
	    if (i > 0)
		sb.append (", ");
	    sb.append ("i: ").append (buffers[i].remaining ());
	}
	return sb.toString ();
    }

    @Override public void timeout () {
	releaseBuffer ();
	sender.timeout ();
    }

    @Override public void closed () {
	releaseBuffer ();
	sender.failed (new IOException ("channel was closed"));
    }

    public void write () {
	try {
	    writeBuffer ();
	} catch (IOException e) {
	    releaseBuffer ();
	    sender.failed (e);
	}
    }
    
    private void writeBuffer () throws IOException {
	long written;
	do {
	    written = getChannel ().write (buffers);
	    tl.write (written);
	} while (written > 0 && end.remaining () > 0);

	if (end.remaining () == 0) {
	    releaseBuffer ();
	    sender.blockSent ();
	} else {
	    waitForWrite (this);
	}
    }
}    

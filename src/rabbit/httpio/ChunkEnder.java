package rabbit.httpio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import rabbit.io.BufferHandle;
import rabbit.io.SimpleBufferHandle;
import rabbit.nio.NioHandler;
import rabbit.util.TrafficLogger;

/** A class that sends the chunk ending (with an empty footer).
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ChunkEnder {
    private static final byte[] CHUNK_ENDING = 
    new byte[] {'0', '\r', '\n', '\r', '\n'};

    public void sendChunkEnding (SocketChannel channel, NioHandler nioHandler,
				 TrafficLogger tl, BlockSentListener bsl) 
	throws IOException {
	ByteBuffer bb = ByteBuffer.wrap (CHUNK_ENDING);
	BufferHandle bh = new SimpleBufferHandle (bb);
	BlockSender bs = 
	    new BlockSender (channel, nioHandler, tl, bh, false, bsl);
	bs.write ();
    }
}

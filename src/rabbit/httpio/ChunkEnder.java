package rabbit.httpio;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.khelekore.rnio.NioHandler;
import rabbit.io.BufferHandle;
import rabbit.io.SimpleBufferHandle;
import rabbit.util.TrafficLogger;

/** A class that sends the chunk ending (with an empty footer).
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ChunkEnder {
    private static final byte[] CHUNK_ENDING = 
    new byte[] {'0', '\r', '\n', '\r', '\n'};

    public void sendChunkEnding (SocketChannel channel, NioHandler nioHandler,
				 TrafficLogger tl, BlockSentListener bsl) {
	ByteBuffer bb = ByteBuffer.wrap (CHUNK_ENDING);
	BufferHandle bh = new SimpleBufferHandle (bb);
	BlockSender bs = 
	    new BlockSender (channel, nioHandler, tl, bh, false, bsl);
	bs.write ();
    }
}

package rabbit.httpio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import rabbit.io.BufferHandle;
import rabbit.io.SimpleBufferHandle;
import rabbit.util.Logger;
import rabbit.util.TrafficLogger;

/** A class that sends the chunk ending (with an empty footer).
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ChunkEnder {
    private static final byte[] CHUNK_ENDING = 
    new byte[] {'0', '\r', '\n', '\r', '\n'};

    public void sendChunkEnding (SocketChannel channel, Selector selector, 
				 Logger logger, TrafficLogger tl, 
				 BlockSentListener bsl) 
	throws IOException {
	ByteBuffer bb = ByteBuffer.wrap (CHUNK_ENDING);
	BufferHandle bh = new SimpleBufferHandle (bb);
	new BlockSender (channel, selector, logger, tl, bh, false, bsl);
    }
}

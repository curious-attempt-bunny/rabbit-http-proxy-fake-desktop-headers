package rabbit.nio;

/** A handler that signals that data is ready to be written.
 */
public interface WriteHandler extends SocketChannelHandler {
    
    /** The channel is ready for read. */
    void write ();
}
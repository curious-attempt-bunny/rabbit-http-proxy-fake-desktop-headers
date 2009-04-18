package rabbit.nio;

/** A handler for socket operations.
 */
public interface SocketChannelHandler {
    /** Signal that the channel has been closed. */
    void closed ();
    
    /** Signal that the select operation timed out. */
    void timeout ();

    /** Check if this handler needs to run in a separate thread. */
    boolean useSeparateThread ();

    /** Get a string description. */
    String getDescription ();

    /** Get the timeout time in millis. 
     * @return the time when this operation times out, 
     *         null if no timeout is set.
     */
    Long getTimeout ();
}

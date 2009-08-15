package rabbit.nio;

import java.nio.channels.SelectableChannel;

/** A handler of nio based events.
 */
public interface NioHandler {

    /** Start handling operations. 
     */
    void start ();
    
    /** Shutdown this task runner. 
     */
    void shutdown ();

    /** Get the default timeout time for an operations started at 
     *  this point in time. 
     */
    Long getDefaultTimeout ();
    
    /** Run a task in a background thread. 
     *  The task will be run sometime in the future.
     * @param r the task to run.    
     */
    void runThreadTask (Runnable r);

    /** Install an event listener for read events.
     *  When the channels is ready the ReadHandler.read () method will 
     *  be called and read selection will be turned off for the channel.
     */ 
    void waitForRead (SelectableChannel channel, ReadHandler handler);

    /** Install an event listener for write events.
     *  When the channel is ready the WriteHandler.write () method will 
     *  be called and write selection will be turned off for the channel.
     */ 
    void waitForWrite (SelectableChannel channel, WriteHandler handler);

    /** Install an event listener for accent events.
     *  When the channel is ready the Accepthandler.accept () method will 
     *  be called and accept selection will be turned off for the channel.
     */ 
    void waitForAccept (SelectableChannel channel, AcceptHandler handler);

    /** Install an event listener for connect events.
     *  When the channel is ready the Connecthandler.connect () method will 
     *  be called and connect selection will be turned off for the channel.
     */ 
    void waitForConnect (SelectableChannel channel, ConnectHandler handler);

    /** Remove an event listener.
     */
    void cancel (SelectableChannel channel, SocketChannelHandler handler);

    /** Close the given channel.
     *  Closing a channel will cause SocketChannelHandler.close () to be
     *  raised on any listeners for this channel and will then cancel
     *  all selector interaction.
     */
    void close (SelectableChannel channel);

    /** Visit all the selectors. 
     *  This should really only be used for status handling and/or 
     *  debugging. 
     */
    void visitSelectors (SelectorVisitor visitor);
}

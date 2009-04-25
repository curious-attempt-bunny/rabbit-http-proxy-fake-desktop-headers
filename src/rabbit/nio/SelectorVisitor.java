package rabbit.nio;

import java.nio.channels.Selector;

/** A visitor of the selectors used by a NioHandler.
 *  The method selector will be called once for each 
 *  of the different selectors used by the NioHandler.
 */
public interface SelectorVisitor {
    /** Visit one selector.
     */
    void selector (Selector selector);

    /** Indicates that all selectors have been visited
     */ 
    void  end ();
}

package rabbit.nio;

/** Identifier for a long and/or slow operation.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface TaskIdentifier {
    /** Get the group id.
     *  The group id is a category name for example "DNS Lookup".
     */
    String getGroupId ();
    
    /** Get the description of this task.
     *  The description provides additional information about
     *  the task and can be something like "dns lookup of yahoo.com"
     */
    String getDescription ();
}
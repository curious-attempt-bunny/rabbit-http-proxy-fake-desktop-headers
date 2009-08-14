package rabbit.nio;

/** A basic immutable task identifier
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class DefaultTaskIdentifier implements TaskIdentifier {
    private final String groupId;
    private final String description;
    
    public DefaultTaskIdentifier (String groupId, String description) {
	this.groupId = groupId;
	this.description = description;
    }

    public String getGroupId () {
	return groupId;
    }
    
    public String getDescription () {
	return description;
    }
}

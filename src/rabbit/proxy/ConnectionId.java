package rabbit.proxy;

/** The id for a connection. 
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ConnectionId {
    private final int group;
    private final long id;
    
    public ConnectionId (int group, long id) {
	this.group = group;
	this.id = id;
    }

    @Override public String toString () {
	StringBuilder sb = new StringBuilder ();
	sb.append ("[").append (group).append (",").append (id).append ("]");
	return sb.toString ();
    }
}

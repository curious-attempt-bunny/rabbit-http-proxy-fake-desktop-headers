package rabbit.filter;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.SocketChannel;
import java.util.List;
import rabbit.util.IPAccess;
import rabbit.util.SProperties;

/** This interface holds the method needed to do socket based access filtering.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */ 
public interface IPAccessFilter {

    /** Filter based on a socket.
     * @param s the Socket to check.
     * @return true if the Socket should be allowed, false otherwise.
     */
    boolean doIPFiltering (SocketChannel s);

    /** Setup this filter.
     * @param properties the SProperties to get the settings from.
     */
    void setup (SProperties properties);

    /** Get the list of allowed ips
     */
    public List<IPAccess> getAllowList ();

    /** Get the list of denied ips
     */
    public List<IPAccess> getDenyList ();
    
    /** Loads in the accessess allowed from the given Reader
     * @param r the Reader were data is available
     */
    public void loadAccess (Reader r) throws IOException;

    /** Saves the accesslist from the given Reader.
     * @param r the Reader with the users.
     */
    public void saveAccess (Reader r) throws IOException;
}

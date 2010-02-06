package rabbit.proxy;

import java.io.IOException;
import org.khelekore.rnio.NioHandler;
import rabbit.cache.Cache;
import rabbit.cache.CacheEntry;
import rabbit.http.HttpHeader;
import rabbit.httpio.FileResourceSource;
import rabbit.io.BufferHandler;

/** A resource that comes from the cache.
 * 
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class CacheResourceSource extends FileResourceSource {
    public CacheResourceSource (Cache<HttpHeader, HttpHeader> cache,
				CacheEntry<HttpHeader, HttpHeader> entry, 
				NioHandler tr, BufferHandler bufHandler) 
	throws IOException {
	super (cache.getEntryName (entry.getId (), true, null), tr, bufHandler);
    }
}

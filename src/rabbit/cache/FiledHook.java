package rabbit.cache;

import java.io.IOException;
import java.util.logging.Logger;

/** A class to store the cache entrys data hook on file. 
 *  A Http Header is a big thing so it is nice to write it to disk. 
 *
 * @param <V> the type of the data stored in files.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class FiledHook<V> extends FileData<V> {
    private static final long serialVersionUID = 20050430;

    protected String getExtension () {
	return "hook";
    }

    private <K> String getFileName (Cache<K, V> cache, 
				    CacheEntry<K, V> entry) {
	return cache.getEntryName (entry.getId (), true, getExtension ());
    }
    
    /** Get the hooked data. 
     * @param <K> the type of the keys used in the cache
     * @param cache the Caching reading the data
     * @param entry the CacheEntry that holds the data
     * @param logger the Logger to use
     * @return the data read from the file cache
     * @throws IOException if reading the data fails
     */
    public <K> V getData (Cache<K, V> cache, CacheEntry<K, V> entry,
			  Logger logger) throws IOException {
	return readData (getFileName (cache, entry),
			 cache.getHookFileHandler (), 
			 logger);
    }

    /** Set the hooked data. 
     * @param <K> the type of the keys used in the cache
     * @param cache the Caching storing the data
     * @param entry the CacheEntry that holds the data
     * @param fh the FileHandler used to do the data conversion
     * @param hook the data to store
     * @param logger the Logger to use
     * @return the size of the file that was written
     * @throws IOException if reading the data fails
     */
    protected <K> long storeHook (Cache<K, V> cache, 
				  CacheEntry<K, V> entry, 
				  FileHandler<V> fh, 
				  V hook,
				  Logger logger) 
	throws IOException {
	return writeData (getFileName (cache, entry), fh, hook, logger);
    }    
}

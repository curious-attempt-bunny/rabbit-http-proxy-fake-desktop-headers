package rabbit.cache;

/** A class to store the cache entrys data hook on file. 
 *  A Http Header is a big thing so it is nice to write it to disk. 
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
     */
    public <K> V getData (Cache<K, V> cache, CacheEntry<K, V> entry) {
	return readData (getFileName (cache, entry), 
			 cache.getHookFileHandler ());
    }

    /** Set the hooked data. 
     */
    protected <K> long storeHook (Cache<K, V> cache, 
				  CacheEntry<K, V> entry, 
				  FileHandler<V> fh, 
				  V hook) {
	return writeData (getFileName (cache, entry), fh, hook);
    }    
}

package rabbit.cache;

import java.util.Collection;
import java.util.logging.Logger;

/** A cache, mostly works like a map in lookup, insert and delete.
 *  A cache may be persistent over sessions. 
 *  A cache may clean itself over time.
 *
 * @param <K> the key type of the cache
 * @param <V> the data resource
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface Cache<K, V> {

    /** Get the cache configuration for this cache.
     * @return the current configuration of the cache 
     */
    public CacheConfiguration getCacheConfiguration ();

    /** Get the current size of the cache
     * @return the current size of the cache in bytes.
     */
    long getCurrentSize ();

    /** Get the current number of entries in the cache.
     * @return the current number of entries in the cache.
     */
    long getNumberOfEntries ();

    /** Get the CacheEntry assosiated with given object.
     * @param k the key.
     * @return the NCacheEntry or null (if not found).
     * @throws CacheException upon failure to get the key
     */ 
    CacheEntry<K, V> getEntry (K k) throws CacheException;

    /** Get the file name for a cache entry. 
     * @param id the id of the cache entry
     * @param real false if this is a temporary cache file, 
     *             true if it is a realized entry.
     * @param extension the cache entry extension.
     * @return the file name of the new entry
     */
    String getEntryName (long id, boolean real, String extension);

    /** Get the file handler for the keys.
     * @return the FileHandler for the key objects
     */
    FileHandler<K> getKeyFileHandler ();

    /** Get the file handler for the values.
     * @return the FileHandler for the values
     */
    FileHandler<V> getHookFileHandler ();
    
    /** Reserve space for a CacheEntry with key o.
     * @param k the key for the CacheEntry.
     * @return a new CacheEntry initialized for the cache.
     */
    CacheEntry<K, V> newEntry (K k);

    /** Insert a CacheEntry into the cache.
     * @param ent the CacheEntry to store.
     * @throws CacheException if adding the entry fails
     */
    void addEntry (CacheEntry<K, V> ent) throws CacheException;

    /** Signal that a cache entry have changed.
     * @param ent the CacheEntry that changed
     * @param newKey the new key of the entry
     * @param newValue the new value
     * @throws CacheException if updating the cache fails
     */
    void entryChanged (CacheEntry<K, V> ent, K newKey, V newValue)
	throws CacheException;

    /** Remove the Entry with key o from the cache.
     * @param k the key for the CacheEntry.
     * @throws CacheException if removal fails
     */
    void remove (K k) throws CacheException;

    /** Clear the Cache from files.
     * @throws CacheException if the clear operation failed
     */
    void clear () throws CacheException;

    /** Get the CacheEntries in the cache.
     * @return an Enumeration of the CacheEntries.
     */    
    Collection<? extends CacheEntry<K, V>> getEntries ();

    /** Make sure that the cache is written to the disk.
     */
    void flush ();
    
    /** Stop this cache. 
     *  If this cache is using any cleaner threads they have 
     *  to be stopped when this method is called.
     */
    void stop ();

    /** Get the logger of this cache 
     * @return the Logger used by the cache
     */
    Logger getLogger ();
}

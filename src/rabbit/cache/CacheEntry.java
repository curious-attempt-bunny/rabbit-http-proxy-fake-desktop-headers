package rabbit.cache;

/** A cached object.
 *
 * @param <K> the key type of this cache entry
 * @param <V> the data resource
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface CacheEntry<K, V> {

    /** Get the id of the entry.
     * @return the id of the entry.
     */
    public long getId ();

    /** Get the key were holding data for
     * @return the key object
     * @throws CacheException if retrieving the key object fails
     */
    K getKey () throws CacheException;

    /** Get the date this object was cached.
     * @return a date (millis since the epoch).
     */
    long getCacheTime ();

    /** Get the size of our file
     * @return the size of our data
     */
    long getSize ();

    /** Get the disk size of the key, if any.
     * @return the size of the key
     */
    long getKeySize ();

    /** Get the disc size of the hook, if any
     * @return the size of the value
     */
    long getHookSize ();

    /** Sets the size of our data file
     * @param size the new Size
     */
    void setSize (long size);

    /** Get the expiry-date of our file
     * @return the expiry date of our data
     */
    long getExpires ();

    /** Sets the expirydate of our data
     * @param d the new expiry-date.
     */
    void setExpires (long d);	

    /** Get the hooked data.
     * @param cache the Cache this entry lives in. 
     * @return the the hooked data.
     * @throws CacheException if getting the value fails
     */
    V getDataHook (Cache<K, V> cache) throws CacheException;

    /** Sets the data hook for this cache object.
     *  Since it is not always possible to make the key hold this...
     * @param o the new data.
     */
    void setDataHook (V o);
}

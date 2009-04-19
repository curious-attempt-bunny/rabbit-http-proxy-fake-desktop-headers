package rabbit.cache;

/** A class that stores cache keys in compressed form. 
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class FiledKey<K> extends FileData<K> {
    private static final long serialVersionUID = 20050430;

    protected int hashCode; // the hashCode for the contained object.
    private long id;
    protected transient Cache<K, ?> cache;
    
    protected String getExtension () {
	return "key";
    }

    protected <V> void setCache (Cache<K, V> cache) {
	this.cache = cache;
    }
    
    protected <V> long storeKey (Cache<K, V> cache, 
				 CacheEntry<K, V> entry, K key) {
	setCache (cache);
	hashCode = key.hashCode ();
	id = entry.getId ();
	return writeData (getFileName (), cache.getKeyFileHandler (), key);
    }

    private String getFileName () {
	return cache.getEntryName (id, true, getExtension ()); 
    }
    
    /** Get the hashCode for the contained key object. */
    @Override public int hashCode () {
	return hashCode;
    }

    /** Check if the given object is equal to the contained key. */
    @Override public boolean equals (Object data) {
	K myData = getData ();
	if (data instanceof FiledKey) {
	    data = ((FiledKey)data).getData ();
	}
	if (myData != null) {
	    return myData.equals (data);
	}
	return data == null;
    }
    
    /** Get the actual key object. */
    public K getData () {
	return readData (getFileName (), cache.getKeyFileHandler ());
    }

    /** Get the unique id for this object. */
    public long getId () {
	return id;
    }

    @Override public String toString () {
	return "FiledKey: " + hashCode + ", " + getFileName ();
    }
}

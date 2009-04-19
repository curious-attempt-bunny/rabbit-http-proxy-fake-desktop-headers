package rabbit.cache;

/** A key to use when searching the cache.
 *
 *  This class only exists to trick equals/hashCode that we
 *  have the same key. 
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class MemoryHook<V> extends FiledHook<V> {
    private static final long serialVersionUID = 20060606;
    private final V data;

    public MemoryHook (V data) {
	this.data = data;
    }

    @Override public <K> V getData (Cache<K, V> cache, CacheEntry<K, V> entry) {
	return data;
    }
}

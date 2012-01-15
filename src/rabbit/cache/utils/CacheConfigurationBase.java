package rabbit.cache.utils;

import rabbit.cache.CacheConfiguration;

/** A base implementation of cache configuration.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public abstract class CacheConfigurationBase implements CacheConfiguration {
    private long maxSize = 0;
    private long cacheTime = 0;

    public synchronized long getMaxSize () {
	return maxSize;
    }

    public synchronized void setMaxSize (long newMaxSize) {
	maxSize = newMaxSize;
    }

    public synchronized long getCacheTime () {
	return cacheTime;
    }

    public synchronized void setCacheTime (long newCacheTime) {
	cacheTime = newCacheTime;
    }
}

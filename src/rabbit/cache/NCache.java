package rabbit.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import rabbit.io.FileHelper;
import rabbit.util.SProperties;

/** The NCache is like a Map in lookup/insert/delete
 *  The NCache is persistent over sessions (saves itself to disk).
 *  The NCache is selfcleaning, that is it removes old stuff.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class NCache<K, V> implements Cache<K, V>, Runnable {
    private static String DIR = "/tmp/rabbit/cache";  // standard dir.
    private static String DEFAULT_SIZE  = "10";       // 10 MB.
    private static String DEFAULT_CACHE_TIME = "24";  // 1 day.
    private static String DEFAULT_CLEAN_LOOP = "60";  // 1 minute

    private static String CACHEINDEX = "cache.index"; // the indexfile.
    private static String TEMPDIR = "temp";
    private static int filesperdir = 256;             // reasonable?

    private boolean changed = false;                  // have we changed?
    private Thread cleaner = null;                    // remover of old stuff.
    private int cleanLoopTime = 60 * 1000;      // sleeptime between cleanups.

    private long maxSize = 0;
    private long cacheTime = 0;
    private long fileNo = 0;
    private long currentSize = 0;
    private String dir = null;
    private Map<FiledKey<K>, CacheEntry<K, V>> htab = null;
    private List<CacheEntry<K, V>> vec = null;
    
    private File tempdir = null;
    private final Object dirLock = new Object ();

    private final Logger logger = Logger.getLogger (getClass ().getName ());

    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock ();
    private final Lock r = rwl.readLock ();
    private final Lock w = rwl.writeLock ();

    private final FileHandler<K> fhk;
    private final FileHandler<V> fhv;

    public boolean running = true;

    /** Create a cache that uses default values.
     *  Note that you must call startCleaner to have the cache fully up.
     */
    public NCache (SProperties props, FileHandler<K> fhk, FileHandler<V> fhv) 
	throws IOException {
	this.fhk = fhk;
	this.fhv = fhv;
	htab = new HashMap<FiledKey<K>, CacheEntry<K, V>> ();
	vec = new ArrayList<CacheEntry<K, V>> ();
	setup (props);
    } 

    public void startCleaner () {
	cleaner = new Thread (this, getClass ().getName () + ".cleaner");
	cleaner.setDaemon (true);
	cleaner.start ();
    }

    public URL getCacheDir () {
	r.lock (); 
	try { 
	    if (dir == null)
		return null;
	    return new File (dir).toURI ().toURL ();
	} catch (MalformedURLException e) {
	    return null;
	} finally {
	    r.unlock ();
	}
    }
    
    /** Sets the cachedir. This will flush the cache and make 
     *  it try to read in the cache from the new dir.
     * @param newDir the name of the new directory to use.
     */
    public void setCacheDir (String newDir) throws IOException {
	w.lock ();
	try {
	    // save old cachedir.
	    if (dir != null)
		writeCacheIndex ();  
	
	    // does new dir exist?
	    dir = newDir;
	    File dirtest = new File (dir);
	    boolean readCache = true;
	    if (!dirtest.exists ()) {
		FileHelper.mkdirs (dirtest);
		if (!dirtest.exists ()) {
		    logger.warning ("could not create cachedir: " + dirtest);
		}
		readCache = false;
	    } else if (dirtest.isFile ()) {
		logger.warning ("Cachedir: " + dirtest + " is a file");
	    }
	
	    synchronized (dirLock) {
		tempdir = new File (dirtest, TEMPDIR);
		if (!tempdir.exists ()) {
		    FileHelper.mkdirs (tempdir);
		    if (!tempdir.exists ()) {
			logger.warning ("could not create cache tempdir: " +
					tempdir);
		    }
		} else if (tempdir.isFile ()) {
		    logger.warning ("Cache temp dir is a file: " + tempdir);
		}
	    }
	    if (readCache)
		// move to new dir.
		readCacheIndex ();
	} finally {
	    w.unlock ();
	}
    }

    /** Get the maximum size for this cache.
     * @return the maximum size in bytes this cache.
     */
    public long getMaxSize () {
	return maxSize;
    }
    
    /** Set the maximum size for this cache.
     * @param newMaxSize the new maximum size for the cache.
     */
    public void setMaxSize (long newMaxSize) {
	maxSize = newMaxSize;
    }

    /** Get the number of miliseconds the cache stores things usually.
     *  This is the standard expiretime for objects, but you can set it for
     *  CacheEntries individially if you want to.
     *  NOTE 1: dont trust that an object will be in the cache this long.
     *  NOTE 2: dont trust that an object will be removed from the cache 
     *          when it expires.
     * @return the number of miliseconds objects are stored normally.
     */
    public long getCacheTime () {
	return cacheTime;
    }
    
    /** Set the standard expiry-time for CacheEntries
     * @param newCacheTime the number of miliseconds to keep objects normally.
     */
    public void setCacheTime (long newCacheTime) {
	cacheTime = newCacheTime;
    }

    /** Get how long time the cleaner sleeps between cleanups.
     */
    public int getCleanLoopTime () {
	return cleanLoopTime;
    }

    /** Set how long time the cleaner sleeps between cleanups.
     * @param newCleanLoopTime the number of miliseconds to sleep. 
     */
    public void setCleanLoopTime (int newCleanLoopTime) {
	cleanLoopTime = newCleanLoopTime;
    }
    
    /** Get the current size of the cache
     * @return the current size of the cache in bytes.
     */
    public long getCurrentSize () {
	r.lock ();
	try {
	    return currentSize;
	} finally {
	    r.unlock ();
	}
    }

    /** Get the current number of entries in the cache.
     * @return the current number of entries in the cache.
     */
    public long getNumberOfEntries () {
	r.lock ();
	try {
	    return htab.size ();
	} finally {
	    r.unlock ();
	}
    }
    
    /** Check that the data hook exists. 
     */
    private boolean checkHook (CacheEntry<K, V> e) {
	if (e instanceof NCacheEntry) {
	    NCacheEntry<K, V> ne = (NCacheEntry<K, V>)e;
	    FiledHook<V> hook = ne.getRealDataHook ();
	    if (hook != null) {
		String entryName = getEntryName (e.getId (), true, "hook");
		File f = new File (entryName);
		if (!f.exists ())
		    return false;
	    }
	    // no hook is legal.
	    return true;
	} 
	return false;
    }

    /** Get the CacheEntry assosiated with given object.
     * @param k the key.
     * @return the CacheEntry or null (if not found).
     */ 
    public CacheEntry<K, V> getEntry (K k) {
	CacheEntry<K, V> ent;
	r.lock ();
	try {
	    ent = htab.get (new MemoryKey<K> (k));
	} finally {
	    r.unlock ();
	}
	if (ent != null && !checkHook (ent)) {
	    // bad entry...
	    remove (ent.getKey ());
	}
	/* If you want to implement LRU or something like that: 
	   if (ent != null)
	       ent.setVisited (new Date ());
	*/
	return ent;
    }

    /** Get the file name for a cache entry. 
     * @param id the id of the cache entry
     * @param real false if this is a temporary cache file, 
     *             true if it is a realized entry.
     */
    public String getEntryName (long id, boolean real, String extension) {
	StringBuilder sb = new StringBuilder (50);
	sb.append (dir);
	sb.append (File.separator);
	if (!real) {
	    sb.append (TEMPDIR);
	} else {
	    long fdir = id / filesperdir;
	    sb.append (fdir);
	}
	sb.append (File.separator);
	sb.append (id);
	if (extension != null) 
	    sb.append ('.').append (extension);
	return sb.toString ();
    }


    /** Reserve space for a CacheEntry with key o.
     * @param k the key for the CacheEntry.
     * @return a new CacheEntry initialized for the cache.
     */
    public CacheEntry<K, V> newEntry (K k) {
	long newId = 0;
	// allocate the id for the new entry.
	w.lock ();
	try {
	    newId = fileNo;
	    fileNo++;
	} finally {
	    w.unlock ();
	}
	// allocate the entry.
	NCacheEntry<K, V> entry = new NCacheEntry<K, V> (k, newId);
	entry.setExpires (System.currentTimeMillis () + getCacheTime ());
	return entry;
    }

    public FileHandler<K> getKeyFileHandler () {
	return fhk;
    }

    public FileHandler<V> getHookFileHandler () {
	return fhv;
    }

    /** Insert a CacheEntry into the cache.
     * @param ent the CacheEntry to store.
     */
    public void addEntry (CacheEntry<K, V> ent) {
	if (ent == null)
	    return;	
	File cfile = new File (getEntryName (ent.getId (), false, null)); 
	if (!cfile.exists()) {
	    return;
	}

	long fdir = ent.getId () / filesperdir;
	File f = new File (dir, "" + fdir);
	String newName = getEntryName (ent.getId (), true, null); 
	File nFile = new File (newName);
	synchronized (dirLock) {
	    if (f.exists ()) {
		if (f.isFile ()) {
		    logger.warning ("Wanted cachedir is a file: " + f);
		}
		// good situation...
	    } else {
		try {
		    FileHelper.mkdirs (f);
		} catch (IOException e) {
		    logger.log (Level.WARNING,
				"Could not create directory: " + f,
				e);
		}
	    }
	    if (!cfile.renameTo (nFile)) 
		logger.severe ("Failed to renamve file from: " + 
			       cfile.getAbsolutePath () + " to" + 
			       nFile.getAbsolutePath ());
	}
	cfile = new File (newName);	
	ent.setSize (cfile.length ());
	ent.setCacheTime (System.currentTimeMillis ());

	NCacheEntry<K, V> nent = (NCacheEntry<K, V>)ent;
	storeHook (nent);	
	K realKey = ent.getKey ();
	FiledKey<K> fk = new FiledKey<K> ();
	fk.storeKey (this, ent, realKey);
	w.lock ();
	try {
	    nent.setKey (fk);
	    remove (realKey);
	    htab.put (fk, ent);
	    currentSize += 
		ent.getSize () + ent.getKeySize () + ent.getHookSize ();
	    vec.add (ent);
	} finally {
	    w.unlock ();
	}
	
	changed = true;
    }

    private void storeHook (NCacheEntry<K, V> nent) {
	V hook = nent.getDataHook (this);
	if (hook != null) {
	    FiledHook<V> fh = new FiledHook<V> ();
	    fh.storeHook (this, nent, getHookFileHandler (), hook);
	    nent.setFiledDataHook (fh);
	}
    }

    /** Signal that a cache entry have changed.
     */
    public void entryChanged (CacheEntry<K, V> ent, K newKey, V newHook) {
	NCacheEntry<K, V> nent = (NCacheEntry<K, V>)ent;
	FiledHook<V> fh = new FiledHook<V> ();
	fh.storeHook (this, nent, getHookFileHandler (), newHook);
	nent.setFiledDataHook (fh);
	FiledKey<K> fk = new FiledKey<K> ();
	fk.storeKey (this, nent, newKey);	
	changed = true;	
    }

    private void removeHook (String base, String extension) throws IOException {
	String hookName = base + extension;
	// remove possible hook before file...
	File hfile = new File (hookName);
	if (hfile.exists ())
	    FileHelper.delete (hfile);
    }
    
    /** Remove the Entry with key k from the cache.
     * @param k the key for the CacheEntry.
     */
    public void remove (K k) {
	CacheEntry<K, V> r;
	w.lock ();
	try {
	    if (k == null) {
		// Odd, but seems to happen. Probably removed 
		// by someone else before enumeration gets to it.
		return;    
	    }
	    FiledKey<K> fk = new MemoryKey<K> (k);
	    r = htab.get (fk);
	    if (r != null) {
		// remove entries while it is still in htab.
		vec.remove (r);
		currentSize -= 
		    (r.getSize () + r.getKeySize () + r.getHookSize ());
		htab.remove (fk);
	    }
	} finally {
	    w.unlock ();
	}
		
	if (r != null) {
	    // this removes the key => htab.remove can not work..
	    String entryName = getEntryName (r.getId (), true, null);
	    try {
		removeHook (entryName, ".hook");
		removeHook (entryName, ".key");
		NCacheEntry<K, V> nent = (NCacheEntry<K, V>)r;
		nent.setKey (null);
		r.setDataHook (null);
		File cfile = new File (entryName);
		if (cfile.exists ()) {
		    File p = cfile.getParentFile ();
		    FileHelper.delete (cfile);
		    // Until NT does rename in a nice manner check for tempdir.
		    synchronized (dirLock) {
			if (p.exists () && !p.equals (tempdir)) {		    
			    String ls[] = p.list ();
			    if (ls != null && ls.length == 0)
				FileHelper.delete (p);
			}
		    }
		}
	    } catch (IOException e) {
		logger.log (Level.WARNING,
			    "Could not remove file",
			    e);		
	    }
	}
    }

    /** Clear the Cache from files. 
     */
    public void clear () {
	ArrayList<FiledKey<K>> ls;
	w.lock ();
	try {
	    ls = new ArrayList<FiledKey<K>> (htab.keySet ());
	    for (FiledKey<K> k : ls)
		remove (k.getData ());
	    vec.clear (); // just to be safe.
	    currentSize = 0;
	    changed = true;
	} finally {
	    w.unlock ();
	}
    }

    /** Get the CacheEntries in the cache.
     *  Note! some entries may be invalid if you have a corruct cache.
     * @return an Enumeration of the CacheEntries.
     */    
    public Collection<CacheEntry<K, V>> getEntries () {
	return htab.values ();
    }

    /** Read the info from an old cache.
     */
    @SuppressWarnings( "unchecked" )
    private void readCacheIndex () {
	fileNo = 0;
	currentSize = 0;
	htab = new HashMap<FiledKey<K>, CacheEntry<K, V>> ();
	vec = new ArrayList<CacheEntry<K, V>> ();
	try {
	    String name = dir + File.separator + CACHEINDEX;
	    FileInputStream fis = new FileInputStream (name);
	    ObjectInputStream is = 
		new ObjectInputStream (new GZIPInputStream (fis));
	    fileNo = is.readLong ();
	    currentSize = is.readLong ();
	    int size = is.readInt ();
	    Map<FiledKey<K>, CacheEntry<K, V>> hh = 
		new HashMap<FiledKey<K>, CacheEntry<K, V>> ((int)(size * 1.2));
	    for (int i = 0; i < size; i++) {
		FiledKey<K> fk = (FiledKey<K>)is.readObject ();
		fk.setCache (this);
		NCacheEntry<K, V> entry = (NCacheEntry<K, V>)is.readObject ();
		entry.setKey (fk);
		hh.put (fk, entry);
	    }
	    htab = hh;
	    vec = (List<CacheEntry<K, V>>)is.readObject ();
	    is.close ();
	} catch (IOException e) {
	    logger.log (Level.WARNING, 
			"Couldnt read " + dir + File.separator + CACHEINDEX + 
			", This is bad( but not serius).\nTreating as empty. ",
			e);
	} catch (ClassNotFoundException e) {
	    logger.log (Level.SEVERE, "Couldn't find classes", e);
	}
    }
    
    /** Make sure that the cache is written to the disk.
     */
    public void flush () {
	writeCacheIndex ();
    }

    /** Store the cache to disk so we can reuse it later.
     */
    private void writeCacheIndex () {
	try {
	    String name = dir + File.separator + CACHEINDEX;
	    
	    FileOutputStream fos = new FileOutputStream (name);
	    ObjectOutputStream os = 
		new ObjectOutputStream (new GZIPOutputStream (fos));
	    
	    r.lock ();
	    try {
		os.writeLong (fileNo);
		os.writeLong (currentSize);
		os.writeInt (htab.size ());
		for (Map.Entry<FiledKey<K>, CacheEntry<K, V>> me : 
			 htab.entrySet ()) {
		    os.writeObject (me.getKey ());
		    os.writeObject (me.getValue ());		    
		}
		os.writeObject (vec);
	    } finally {
		r.unlock ();
	    }
	    os.close ();
	} catch (IOException e) {
	    logger.log (Level.WARNING,
			"Couldnt write " + dir + File.separator + CACHEINDEX + 
			", This is serious!\n", 
			e);
	}	
    }

    /** Loop in a cleaning loop.
     */
    public void run () {
	Thread.currentThread ().setPriority (Thread. MIN_PRIORITY);
	while (running) {
	    try {
		Thread.sleep (cleanLoopTime);
	    } catch (InterruptedException e) {
		//System.err.println ("Cache interrupted");
	    }
	    if (!running)
		continue;
	    
	    // actually for a busy cache this will lag...
	    // but I dont care for now... 
	    long milis = System.currentTimeMillis ();
	    Collection<CacheEntry<K, V>> entries;
	    r.lock ();
	    try {
		entries = new ArrayList<CacheEntry<K, V>> (htab.values ());
	    } finally {
		r.unlock ();
	    }
	    for (CacheEntry<K, V> ce : entries) {
		long exp = ce.getExpires ();
		if (exp < milis) 
		    remove (ce.getKey ());
	    }

	    // IF SIZE IS TO BIG REMOVE A RANDOM AMOUNT OF OBJECTS.
	    // What we have to be careful about: we must not remove the same
	    // elements two times in a row, this method remove the "oldest" in 
	    // a sense.

	    if (getCurrentSize () > getMaxSize ())
		changed = true;
	    while (getCurrentSize () > getMaxSize ()) {
		w.lock (); 
		try {
		    remove (vec.get (0).getKey ());
		} finally {
		    w.unlock ();
		}
	    }
	    
	    if (changed) {
		writeCacheIndex ();
		changed = false;
	    }
	}
    }

    public void stop () {
	running = false;
	if (cleaner != null) {
	    try {
		cleaner.interrupt ();
		cleaner.join ();
	    } catch (InterruptedException e) {
		// ignore
	    }
	}
    }

    /** Configure the cache system from the given config.
     * @param config the properties describing the cache settings.
     */
    public void setup (SProperties config) throws IOException {
	if (config == null)
	    config = new SProperties (); 
	String cachedir = 
	    config.getProperty ("directory", DIR);
	setCacheDir (cachedir);

	String cmsize = config.getProperty ("maxsize", DEFAULT_SIZE);
	try {
	    setMaxSize (Long.parseLong (cmsize) * 1024 * 1024);     // in MB
	} catch (NumberFormatException e) { 
	    logger.warning ("Bad number for cache maxsize: '" + cmsize + "'");
	}

	String ctime = config.getProperty ("cachetime", DEFAULT_CACHE_TIME);
	try {
	    setCacheTime (Long.parseLong (ctime) * 1000 * 60 * 60); // in hours.o
	} catch (NumberFormatException e) { 
	    logger.warning ("Bad number for cache cachetime: '" + ctime + "'");
	}

	String ct = config.getProperty ("cleanloop", DEFAULT_CLEAN_LOOP);
	try {
	    setCleanLoopTime (Integer.parseInt (ct) * 1000); // in seconds.
	} catch (NumberFormatException e) { 
	    logger.warning ("Bad number for cache cleanloop: '" + ct + "'");
	}
    }
}

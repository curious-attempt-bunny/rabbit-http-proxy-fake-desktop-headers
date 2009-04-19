package rabbit.util;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** This class counts different messages
 */
public class Counter 
{
    // All the messages we count.
    private Map<String, Count> counters = 
	new ConcurrentHashMap<String, Count> ();
    
    /** This class holds one messages counts
     */
    static class Count {
	private AtomicInteger counter = new AtomicInteger (0);
	
	/** Create a new Count
	 */
	Count () {
	}
	
	/** Increase its value by one
	 */
	void inc () {
	    counter.incrementAndGet ();
	}
	
	/** Get the count for this message
	 * @return the number of times this message has been counted.
	 */
	public int count () {
	    return counter.intValue ();
	}
    }

    /** Increase a logentry.
     * @param log the event to increase 
     */
    public void inc (String log) {
	Count l = counters.get(log);
	if (l == null) {
	    l = new Count ();
	    counters.put (log,l);
	}
	l.inc ();	
    }
    
    /** Get all events
     * @return an Set of all events
     */
    public Set<String> keys () {
	return counters.keySet ();
    }

    /** Get the current count for an event.
     * @param key the event were intrested in
     * @return the current count of event.
     */
    public int get (String key) {
	Count l = counters.get (key);
	if (l == null)
	    return 0;
	return l.count ();
    }
}


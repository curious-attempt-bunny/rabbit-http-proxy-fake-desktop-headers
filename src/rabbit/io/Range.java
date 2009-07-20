package rabbit.io;

/** A class to handle a range.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class Range {
    private final long start;
    private final long end;
    public Range (long start, long end) {
	this.start = start; 
	this.end = end;
    }

    public long getStart () {
	return start;
    }
	
    public long getEnd () {
	return end;
    }

    public long size () {
	// range is inclusive 1-5 has 5 bytes.
	return end - start + 1;
    }
}

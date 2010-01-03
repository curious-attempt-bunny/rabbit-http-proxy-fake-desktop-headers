package rabbit.proxy;

import rabbit.http.HttpHeader;

/** Methods dealing with etags 
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ETagUtils {
    /** Check if the given etag is weak.
     */
    public static boolean isWeak (String t) {
	return t.startsWith ("W/");
    }

    /** Check if we have a strong etag match.
     */
    public static boolean checkStrongEtag (String et, String im) {
	return !isWeak (im) && im.equals (et);
    }

    /** Remove any W/ prefix then check if etags are equal.
     *  Inputs can be in any order.
     * @return true if the etags match or at least one of the etag
     *              headers do not exist.
     */
    public static boolean checkWeakEtag (HttpHeader h1, HttpHeader h2) {
	String et1 = h1.getHeader ("Etag");
	String et2 = h2.getHeader ("Etag");
	if (et1 == null || et2 == null)
	    return true;
	return checkWeakEtag (et1, et2);
    }

    /** Remove any W/ prefix from the inputs then check if they are equal.
     *  Inputs can be in any order.
     * @return true if equal.
     */
    public static boolean checkWeakEtag (String et, String im) {
	if (et == null || im == null)
	    return false;
	if (isWeak (et))
	    et = et.substring (2);
	if (isWeak (im))
	    im = im.substring (2);
	return im.equals (et);
    }
}
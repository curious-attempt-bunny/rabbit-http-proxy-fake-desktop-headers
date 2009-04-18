package rabbit.util;

/** A class that tries to guess mime types based on file extensions.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class MimeTypeMapper {
    public static String getMimeType (String filename) {
	filename = filename.toLowerCase ();
	if (filename.endsWith ("gif"))
	    return "image/gif";
	else if (filename.endsWith ("png"))
	    return "image/png";
	else if (filename.endsWith ("jpeg") || filename.endsWith ("jpg"))
	    return "image/jpeg";
	else if (filename.endsWith ("txt"))
	    return "text/plain";
	else if (filename.endsWith ("html"))
	    return "text/html";
	return null;
    }
}    
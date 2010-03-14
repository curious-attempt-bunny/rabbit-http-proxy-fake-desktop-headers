package rabbit.html;
import org.apache.commons.lang.StringEscapeUtils;

/** Escape strings to make them html-safe.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class HtmlEscapeUtils {
    public static String escapeHtml (String s) {
	return StringEscapeUtils.escapeHtml (s);
    }
}

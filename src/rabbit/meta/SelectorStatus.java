package rabbit.meta;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import rabbit.proxy.HtmlPage;

/** A status page for the proxy.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class SelectorStatus extends BaseMetaHandler {

    @Override protected String getPageHeader () {
	return "Selector status";
    }
  
    /** Add the page information */
    @Override protected PageCompletion addPageInformation (StringBuilder sb) {
	addStatus (sb);
	return PageCompletion.PAGE_DONE;
    }    

    private void addStatus (StringBuilder sb) {
	sb.append ("Status of selector at: ");
	sb.append (new Date ());
	sb.append ("<p>\n");
	/*
	Selector sel = con.getSelector ();
	appendKeys (sb, sel.selectedKeys (), "Selected key");
	appendKeys (sb, sel.keys (), "Registered key");
	*/
    }

    private void appendKeys (StringBuilder sb, 
			     Set<SelectionKey> sks, String header) {
	SimpleDateFormat sdf = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss");
	sb.append (HtmlPage.getTableHeader (100, 1));
	sb.append (HtmlPage.getTableTopicRow ());
	sb.append ("<th width=\"20%\">").append (header).append ("</th>");
	sb.append ("<th width=\"50%\">Attachment</th>");
	sb.append ("<th>Interest</th>");
	sb.append ("<th>Ready</th>");
	sb.append ("<th>Registered</th>");
	sb.append ("</tr>\n");
	for (SelectionKey sk : sks) {
	    sb.append ("<tr><td>");
	    sb.append (sk.toString ());
	    sb.append ("</td><td>");
	    sb.append (sk.attachment ());
	    sb.append ("</td><td>");
	    boolean valid = sk.isValid ();
	    appendOpString (sb, valid ? sk.interestOps () : 0);
	    sb.append ("</td><td>");
	    appendOpString (sb, valid ? sk.readyOps () : 0);
	    sb.append ("</td><td>");
	    Object a = sk.attachment ();
	    /*
	    if (a instanceof HandlerRegistration) {
		HandlerRegistration hr = (HandlerRegistration)a;
		long when = hr.getRegistrationTime ();
		if (when < Long.MAX_VALUE)
		    sb.append (sdf.format (when));
	    }
	    */
	    sb.append ("</td></tr>\n");
	}
	sb.append ("</table>\n<br>\n");
    }
    
    private void appendOpString (StringBuilder sb, int op) {
	sb.append ((op & SelectionKey.OP_READ) != 0 ? "R" : "_");
	sb.append ((op & SelectionKey.OP_WRITE) != 0 ? "W" : "_");
	sb.append ((op & SelectionKey.OP_CONNECT) != 0 ? "C" : "_");
	sb.append ((op & SelectionKey.OP_ACCEPT) != 0 ? "A" : "_");
    }
}

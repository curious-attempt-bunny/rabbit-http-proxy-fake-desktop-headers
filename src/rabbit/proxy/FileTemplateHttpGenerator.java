package rabbit.proxy;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.khelekore.rnio.impl.Closer;
import rabbit.http.HttpHeader;
import rabbit.http.StatusCode;

import static rabbit.http.StatusCode.*;

/** A HttpGenerator that creates error pages from file templates.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class FileTemplateHttpGenerator extends StandardResponseHeaders {

    private final File templateDir;
    private final Logger logger = Logger.getLogger (getClass ().getName ());

    public FileTemplateHttpGenerator (String identity,
				      Connection con,
				      File templateDir) {
	super (identity, con);
	this.templateDir = templateDir;
    }

    private File getFile (StatusCode sc) {
	return new File (templateDir, Integer.toString (sc.getCode ()));
    }

    private boolean hasFile (StatusCode sc) {
	return getFile (sc).exists ();
    }

    private String replaceText (StatusCode sc, String template) {
	// TODO: replace
	return template;
    }

    private HttpHeader getTemplated (StatusCode sc) {
	HttpHeader ret = getHeader (sc);
	File f = getFile (sc);
	try {
	    FileInputStream fis = new FileInputStream (f);
	    try {
		byte[] buf = new byte[(int)f.length ()];
		DataInputStream dis = new DataInputStream (fis);
		try {
		    dis.readFully (buf);
		    String s = new String (buf, "UTF-8");
		    s = replaceText (sc, s);
		    ret.setContent (s);
		} finally {
		    Closer.close (dis, logger);
		}
	    } finally {
		Closer.close (fis, logger);
	    }
	} catch (IOException e) {
	    logger.log (Level.WARNING, "Failed to read template", e);
	}
	return ret;
    }

    @Override public HttpHeader get400 (Exception exception) {
	if (hasFile (_400))
	    return getTemplated (_400);
	return super.get400 (exception);
    }

    @Override public HttpHeader get401 (String realm, URL url) {
	if (hasFile (_401))
	    return getTemplated (_401);
	return super.get401 (realm, url);
    }

    @Override public HttpHeader get403 () {
	if (hasFile (_403))
	    return getTemplated (_403);
	return super.get403 ();
    }

    @Override public HttpHeader get404 (String file) {
	if (hasFile (_404))
	    return getTemplated (_404);
	return super.get404 (file);
    }

    @Override public HttpHeader get407 (String realm, URL url) {
	if (hasFile (_407))
	    return getTemplated (_407);
	return super.get407 (realm, url);
    }

    @Override public HttpHeader get412 () {
	if (hasFile (_412))
	    return getTemplated (_412);
	return super.get412 ();
    }

    @Override public HttpHeader get414 () {
	if (hasFile (_414))
	    return getTemplated (_414);
	return super.get414 ();
    }

    @Override public HttpHeader get416 (Throwable exception) {
	if (hasFile (_416))
	    return getTemplated (_416);
	return super.get416 (exception);
    }

    @Override public HttpHeader get417 (String expectation) {
	if (hasFile (_417))
	    return getTemplated (_417);
	return super.get417 (expectation);
    }

    @Override public HttpHeader get500 (Throwable exception) {
	if (hasFile (_500))
	    return getTemplated (_500);
	return super.get500 (exception);
    }

    @Override public HttpHeader get504 (Throwable exception, String uri) {
	if (hasFile (_504))
	    return getTemplated (_504);
	return super.get504 (exception, uri);
    }
}

package rabbit.handler;

import java.io.File;
import java.io.IOException;
import rabbit.http.HttpHeader;
import rabbit.httpio.FileResourceSource;
import rabbit.httpio.ResourceSource;
import rabbit.io.BufferHandle;
import rabbit.io.FileHelper;
import rabbit.proxy.Connection;
import rabbit.proxy.HttpProxy;
import rabbit.proxy.TrafficLoggerHandler;
import rabbit.util.SProperties;

/** This image handler uses an external program to convert images.
 *  The default converter is the program "convert" from imagemagick.
 *  Using graphicsmagick with "gm convert" also seems to work fine.
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ImageHandler extends ImageHandlerBase {
    private static final String STD_CONVERT = "/usr/bin/convert";
    private static final String STD_CONVERT_ARGS = 
    "-quality 10 -flatten $filename +profile \"*\" jpeg:$filename.c";

    private File typeFile = null;

    /** For creating the factory.
     */
    public ImageHandler () {
    }

    /** Create a new ImageHandler for the given request.
     * @param con the Connection handling the request.
     * @param request the actual request made.
     * @param clientHandle the client side buffer.
     * @param response the actual response.
     * @param content the resource.
     * @param mayCache May we cache this request?
     * @param mayFilter May we filter this request?
     * @param size the size of the data beeing handled.
     */
    public ImageHandler (Connection con, TrafficLoggerHandler tlh,
			 HttpHeader request, BufferHandle clientHandle,
			 HttpHeader response, ResourceSource content,
			 boolean mayCache, boolean mayFilter, long size,
			 SProperties config, boolean doConvert,
			 int minSizeToConvert) {
	super (con, tlh, request, clientHandle, response, content,
	       mayCache, mayFilter, size, config, doConvert, minSizeToConvert);
    }

    @Override
    public Handler getNewInstance (Connection con, TrafficLoggerHandler tlh,
				   HttpHeader header, BufferHandle bufHandle,
				   HttpHeader webHeader,
				   ResourceSource content, boolean mayCache,
				   boolean mayFilter, long size) {
	return new ImageHandler (con, tlh, header, bufHandle, webHeader,
				 content, mayCache, mayFilter, size,
				 getConfig (), getDoConvert (), 
				 getMinSizeToConvert ());
    }

    @Override protected void internalConvertImage () throws IOException {
	long origSize = size;
	String convert = getConfig ().getProperty ("convert", STD_CONVERT);
	String convargs = getConfig ().getProperty ("convertargs", STD_CONVERT_ARGS);

	int idx = 0;
	HttpProxy proxy = con.getProxy ();
	String entryName =
	    proxy.getCache ().getEntryName (entry.getId (), false, null);
	try {
	    while ((idx = convargs.indexOf ("$filename")) > -1) {
		convargs = convargs.substring (0, idx) + entryName +
		    convargs.substring (idx + "$filename".length());
	    }
	    String command = convert + " " + convargs;
	    getLogger ().fine ("ImageHandler running: '" + command + "'");
	    Process ps = Runtime.getRuntime ().exec (command);
	    try {
		ps.waitFor ();
		closeStreams (ps);
		int exitValue = ps.exitValue ();
		if (exitValue != 0) {
		    getLogger ().warning ("Bad conversion: " + entryName +
					  ", got exit value: " + exitValue);
		    throw new IOException ("failed to convert image, " +
					   "exit value: " + exitValue);
		}
	    } catch (InterruptedException e) {
		getLogger ().warning ("Interupted during wait for: " +
				      entryName);
	    }

	    convertedFile = new File (entryName + ".c");
	    typeFile = new File (entryName + ".type");
	    lowQualitySize = convertedFile.length ();
	    if (lowQualitySize > 0 && origSize > lowQualitySize) {
		String ctype = checkFileType (typeFile);
		response.setHeader ("Content-Type", ctype);
		/** We need to remove the existing file first for
		 *  windows system, they will not overwrite files in a move.
		 *  Spotted by: Michael Mlivoncic
		 */
		File oldEntry = new File (entryName);
		if (oldEntry.exists ())
		    FileHelper.delete (oldEntry);
		if (convertedFile.renameTo (new File (entryName)))
		    convertedFile = null;
		else
		    getLogger ().warning ("rename failed: " +
					  convertedFile.getName () +
					  " => " +
					  entryName);
	    }
	} finally {
	    if (convertedFile != null)
		deleteFile (convertedFile);
	    if (typeFile != null && typeFile.exists ())
		deleteFile (typeFile);
	}
	size = (lowQualitySize < origSize ? lowQualitySize : origSize);
	response.setHeader ("Content-length", "" + size);
	con.setExtraInfo ("imageratio:" + lowQualitySize + "/" + origSize +
			  "=" + ((float)lowQualitySize / origSize));
	content.release ();
	content = new FileResourceSource (entryName, con.getNioHandler (),
					  con.getBufferHandler ());
	convertedFile = null;
    }

    @Override public void setup (SProperties prop) {
	super.setup (prop);
	if (prop != null) {
	    String conv = prop.getProperty ("convert", STD_CONVERT);
	    File f = new File (conv);
	    if (!f.exists () || !f.isFile()) {
		getLogger ().warning ("convert -" + conv +
				      "- not found, is your path correct?");
		setDoConvert (false);
	    }
	}
    }
}
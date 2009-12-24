package rabbit.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import rabbit.http.HttpHeader;
import rabbit.httpio.BlockListener;
import rabbit.httpio.FileResourceSource;
import rabbit.httpio.ResourceSource;
import rabbit.io.BufferHandle;
import rabbit.io.Closer;
import rabbit.io.FileHelper;
import rabbit.handler.convert.ExternalProcessConverter;
import rabbit.handler.convert.ImageConverter;
import rabbit.handler.convert.JavaImageConverter;
import rabbit.nio.DefaultTaskIdentifier;
import rabbit.nio.TaskIdentifier;
import rabbit.proxy.Connection;
import rabbit.proxy.HttpProxy;
import rabbit.proxy.TrafficLoggerHandler;
import rabbit.util.SProperties;

/** This handler first downloads the image runs convert on it and
 *  then serves the smaller image.
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ImageHandler extends BaseHandler {
    private SProperties config = new SProperties ();
    private boolean doConvert = true;
    private int minSizeToConvert = 2000;

    private boolean converted = false;
    protected File convertedFile = null;
    private ImageConverter imageConverter;
    
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
			 int minSizeToConvert, ImageConverter imageConverter) {
	super (con, tlh, request, clientHandle, response, content,
	       mayCache, mayFilter, size);
	if (size == -1)
	    con.setKeepalive (false);
	con.setChunking (false);
	this.config = config;
	this.doConvert = doConvert;
	this.minSizeToConvert = minSizeToConvert;
	this.imageConverter = imageConverter;
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
				 getMinSizeToConvert (), imageConverter);
    }
    
    /**
     * ®return true this handler modifies the content.
     */
    @Override public boolean changesContentSize () {
	return true;
    }

    /** Images needs to be cacheable to be compressed.
     * @return true
     */
    @Override protected boolean mayCacheFromSize () {
	return true;
    }

    /** Check if this handler may force the cached resource to be less than
     *  the cache max size.
     * @return false
     */
    @Override protected boolean mayRestrictCacheSize () {
	return false;
    }

    /** Try to convert the image before letting the superclass handle it.
     */
    @Override public void handle () {
	try {
	    tryconvert ();
	} catch (IOException e) {
	    failed (e);
	}
    }

    @Override protected void addCache () {
	if (!converted)
	    super.addCache ();
	// if we get here then we have converted the image
	// and do not want a cache...
    }

    /** clear up the mess we made (remove intermediate files etc).
     */
    @Override protected void finish (boolean good) {
	try {
	    if (convertedFile != null) {
		deleteFile (convertedFile);
		convertedFile = null;
	    }
	} finally {
	    super.finish (good);
	}
    }

    /** Remove the cachestream and the cache entry.
     */
    @Override protected void removeCache () {
	super.removeCache ();
	if (convertedFile != null) {
	    deleteFile (convertedFile);
	    convertedFile = null;
	}
    }


    /** Try to convert the image. This is done like this:
     *  <xmp>
     *  super.addCache ();
     *  readImage();
     *  convertImage();
     *  cacheChannel = null;
     *  </xmp>
     *  We have to use the cachefile to convert the image, and if we
     *  convert it we dont want to write the file to the cache later
     *  on.
     */
    protected void tryconvert () throws IOException {
	if (getLogger ().isLoggable (Level.FINER))
	    getLogger ().finer (request.getRequestURI () + 
				": doConvert: " + doConvert + ", mayFilter: " + 
				mayFilter + ", mayCache: " + mayCache + 
				", size: " + size + ", minSizeToConvert: " + 
				minSizeToConvert);
	// TODO: if the image size is unknown (chunked) we will have -1 > 2000
	// TODO: perhaps we should add something to handle that.
	if (doConvert && mayFilter && mayCache && size > minSizeToConvert) {
	    super.addCache ();
	    // check if cache setup worked.
	    if (cacheChannel == null)
		super.handle ();
	    else
		readImage ();
	} else {
	    super.handle ();
	}
    }

    /** Read in the image
     * @throws IOException if reading of the image fails.
     */
    protected void readImage () throws IOException {
	content.addBlockListener (new ImageReader ());
    }

    private class ImageReader implements BlockListener {
	public void bufferRead (final BufferHandle bufHandle) {
	    TaskIdentifier ti =
		new DefaultTaskIdentifier (getClass ().getSimpleName (),
					   request.getRequestURI ());
	    con.getNioHandler ().runThreadTask (new Runnable () {
		    public void run () {
			writeImageData (bufHandle);
		    }
		}, ti);
	}

	private void writeImageData (BufferHandle bufHandle) {
	    try {
		ByteBuffer buf = bufHandle.getBuffer ();
		writeCache (buf);
		totalRead += buf.remaining ();
		buf.position (buf.limit ());
		bufHandle.possiblyFlush ();
		content.addBlockListener (this);
	    } catch (IOException e) {
		failed (e);
	    }
	}

	public void finishedRead () {
	    try {
		if (size > 0 && totalRead != size)
		    setPartialContent (totalRead, size);
		cacheChannel.close ();
		cacheChannel = null;
		convertImage ();
	    } catch (IOException e) {
		failed (e);
	    }
	}

	public void failed (Exception cause) {
	    ImageHandler.this.failed (cause);
	}

	public void timeout () {
	    ImageHandler.this.failed (new IOException ("Timeout"));
	}
    }

    /** Convert the image into a small low quality image (normally a jpeg).
     * @throws IOException if conversion fails.
     */
    protected void convertImage () {
	TaskIdentifier ti =
	    new DefaultTaskIdentifier (getClass ().getSimpleName () +
				       ".convertImage",
				       request.getRequestURI ());

	con.getNioHandler ().runThreadTask (new Runnable () {
		public void run () {
		    try {
			convertAndGetBest ();
			converted = true;
			ImageHandler.super.handle ();
		    } catch (IOException e) {
			failed (e);
		    }
		}
	    }, ti);
    }

    private void convertAndGetBest () throws IOException {
	HttpProxy proxy = con.getProxy ();
	String entryName =
	    proxy.getCache ().getEntryName (entry.getId (), false, null);

	if (getLogger ().isLoggable (Level.FINER))
	    getLogger ().finer (request.getRequestURI () + 
				": Trying to convert image: " + entryName);
	File entry = new File (entryName);
	ImageConversionResult icr = internalConvertImage (entry, entryName);
	try {
	    convertedFile = selectImage (entry, icr);
	} finally {
	    if (icr.convertedFile != null && icr.convertedFile.exists ())
		deleteFile (icr.convertedFile);
	    convertedFile = null;
	    if (icr.typeFile != null && icr.typeFile.exists ())
		deleteFile (icr.typeFile);
	}

	if (getLogger ().isLoggable (Level.FINER))
	    getLogger ().finer (request.getRequestURI () + 
				": OrigSize: " + icr.origSize + 
				", convertedSize: " + icr.convertedSize);
	size = icr.convertedSize > 0 ? icr.convertedSize : icr.origSize;
	response.setHeader ("Content-length", "" + size);
	double ratio = (double)icr.convertedSize / icr.origSize;
	String sRatio = String.format ("%.3f", ratio);
	con.setExtraInfo ("imageratio:" + icr.convertedSize + "/" + 
			  icr.origSize + "=" + 
			  sRatio);
	content.release ();
	content = new FileResourceSource (entryName, con.getNioHandler (),
					  con.getBufferHandler ());
    }

    public static class ImageConversionResult {
	public final long origSize;
	public final long convertedSize;
	public final File convertedFile;
	public final File typeFile;

	public ImageConversionResult (long origSize, 
				      File convertedFile,
				      File typeFile) {
	    this.origSize = origSize;
	    this.convertedFile = convertedFile;
	    this.typeFile = typeFile;
	    if (convertedFile.exists ())
		this.convertedSize = convertedFile.length ();
	    else 
		convertedSize = 0;
	}

	@Override public String toString () {
	    return getClass ().getSimpleName () + "{origSize: " + 
		origSize + ", convertedSize: " + convertedSize + 
		", convertedFile: " + convertedFile + 
		", typeFile: " + typeFile + "}";
	}
    }

    /** Perform the actual image conversion. 
     * @param entryName the filename of the cache entry to use.
     */
    protected ImageConversionResult 
    internalConvertImage (File input, String entryName) throws IOException {
	long origSize = size;
	convertedFile = new File (entryName + ".c");
	File typeFile = new File (entryName + ".type");
	imageConverter.convertImage (input, convertedFile, 
				     request.getRequestURI ());
	return new ImageConversionResult (origSize, convertedFile, typeFile);
    }

    /** Make sure that the cache entry is the smallest image. 
     */
    private File selectImage (File entry, ImageConversionResult icr) 
	throws IOException {
	File convertedFile = icr.convertedFile;
	if (icr.convertedSize > 0 && icr.origSize > icr.convertedSize) {
	    String ctype = checkFileType (icr.typeFile);
	    response.setHeader ("Content-Type", ctype);
	    /** We need to remove the existing file first for
	     *  windows system, they will not overwrite files in a move.
	     *  Spotted by: Michael Mlivoncic
	     */
	    if (entry.exists ()) {
		if (getLogger ().isLoggable (Level.FINER))
		    getLogger ().finer (request.getRequestURI () + 
					": deleting old entry: " + 
					entry);
		FileHelper.delete (entry);
	    }
	    if (getLogger ().isLoggable (Level.FINER))
		getLogger ().finer (request.getRequestURI () + 
				    ": Trying to move converted file: " + 
				    icr.convertedFile + " => "  + entry);
	    if (icr.convertedFile.renameTo (entry))
		convertedFile = null;
	    else
		getLogger ().warning ("rename failed: " +
				      convertedFile.getName () +
				      " => " + entry);
	}
	return convertedFile;
    }

    protected String checkFileType (File typeFile) throws IOException {
	String ctype = "image/jpeg";
	if (typeFile != null && typeFile.exists () && typeFile.length () > 0) {
	    BufferedReader br = null;
	    try {
		br = new BufferedReader (new FileReader (typeFile));
		ctype = br.readLine();
	    } finally {
		Closer.close (br, getLogger ());
	    }
	}
	return ctype;
    }

    public void setDoConvert (boolean doConvert) {
	this.doConvert = doConvert;
    }

    public boolean getDoConvert () {
	return doConvert;
    }

    public SProperties getConfig () {
	return config;
    }

    public int getMinSizeToConvert () {
	return minSizeToConvert;
    }

    @Override public void setup (SProperties prop) {
	super.setup (prop);
	if (prop == null)
	    return;
	config = prop;
	setDoConvert (true);
	minSizeToConvert =
	    Integer.parseInt (prop.getProperty ("min_size", "2000"));
	String converterType = prop.getProperty ("converter_type", "external");
	if (converterType.equalsIgnoreCase ("external")) {
	    imageConverter = new ExternalProcessConverter (prop);
	    if (!imageConverter.canConvert ()) {
		getLogger ().warning ("imageConverter: " + imageConverter + 
				      " can not convert images, using java.");
		imageConverter = null;
	    }
	} 
	if (imageConverter == null) 
	    imageConverter = new JavaImageConverter (prop);
    }
}

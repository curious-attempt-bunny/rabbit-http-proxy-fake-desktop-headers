package rabbit.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import rabbit.http.HttpHeader;
import rabbit.httpio.BlockListener;
import rabbit.httpio.FileResourceSource;
import rabbit.httpio.ResourceSource;
import rabbit.io.BufferHandle;
import rabbit.io.Closer;
import rabbit.io.FileHelper;
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
public abstract class ImageHandlerBase extends BaseHandler {
    private SProperties config = new SProperties ();
    private boolean doConvert = true;
    private int minSizeToConvert = 2000;

    protected boolean converted = false;
    protected long lowQualitySize = -1;
    protected File convertedFile = null;

    /** For creating the factory.
     */
    public ImageHandlerBase () {
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
    public ImageHandlerBase (Connection con, TrafficLoggerHandler tlh,
			     HttpHeader request, BufferHandle clientHandle,
			     HttpHeader response, ResourceSource content,
			     boolean mayCache, boolean mayFilter, long size,
			     SProperties config, boolean doConvert,
			     int minSizeToConvert) {
	super (con, tlh, request, clientHandle, response, content,
	       mayCache, mayFilter, size);
	if (size == -1)
	    con.setKeepalive (false);
	con.setChunking (false);
	this.config = config;
	this.doConvert = doConvert;
	this.minSizeToConvert = minSizeToConvert;
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

    /** Check if this handler may force the cached resource to be less than the cache max size.
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
	    if (convertedFile != null)
		deleteFile (convertedFile);
	} finally {
	    super.finish (good);
	}
    }

    /** Remove the cachestream and the cache entry.
     */
    @Override protected void removeCache () {
	super.removeCache ();
	if (convertedFile != null)
	    deleteFile (convertedFile);
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
	    ImageHandlerBase.this.failed (cause);
	}

	public void timeout () {
	    ImageHandlerBase.this.failed (new IOException ("Timeout"));
	}
    }

    public void closeStreams (Process ps) throws IOException {
	ps.getInputStream ().close ();
	ps.getOutputStream ().close ();
	ps.getErrorStream ().close ();
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
			internalConvertImage ();
			converted = true;
			ImageHandlerBase.super.handle ();
		    } catch (IOException e) {
			failed (e);
		    }
		}
	    }, ti);
    }

    /** Perform the actual image conversion. */
    protected abstract void internalConvertImage () throws IOException;
    
    protected String checkFileType (File typeFile) throws IOException {
	String ctype = "image/jpeg";
	if (typeFile.exists () && typeFile.length() > 0) {
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
	if (prop != null) {
	    config = prop;
	    setDoConvert (true);
	    minSizeToConvert =
		Integer.parseInt (prop.getProperty ("min_size", "2000"));
	}
    }
}

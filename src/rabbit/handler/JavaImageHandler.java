package rabbit.handler;

import java.awt.Graphics;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import rabbit.handler.convert.JavaImageConverter;
import rabbit.http.HttpHeader;
import rabbit.httpio.ResourceSource;
import rabbit.io.BufferHandle;
import rabbit.proxy.Connection;
import rabbit.proxy.TrafficLoggerHandler;
import rabbit.util.SProperties;

/** This image handler uses standard java imageio to convert images
 *  into low quality jpegs.
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class JavaImageHandler extends ImageHandlerBase {
    private JavaImageConverter jic;

    /** For creating the factory.
     */
    public JavaImageHandler () {
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
    public JavaImageHandler (Connection con, TrafficLoggerHandler tlh,
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
	return new JavaImageHandler (con, tlh, header, bufHandle, webHeader,
				     content, mayCache, mayFilter, size,
				     getConfig (), getDoConvert (), 
				     getMinSizeToConvert ());
    }

    @Override protected ImageConversionResult 
    internalConvertImage (String entryName) throws IOException {
	long origSize = size;
	File input = new File (entryName);
	convertedFile = new File (entryName + ".c");
	File typeFile = new File (entryName + ".type");
	jic.convertImage (input, convertedFile, request.getRequestURI ());
	return new ImageConversionResult (origSize, convertedFile, typeFile);
    }

    @Override public void setup (SProperties prop) {
	jic = new JavaImageConverter (prop);
    }
}

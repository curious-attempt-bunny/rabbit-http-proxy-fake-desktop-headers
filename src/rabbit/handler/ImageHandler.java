package rabbit.handler;

import java.io.File;
import java.io.IOException;
import rabbit.http.HttpHeader;
import rabbit.httpio.ResourceSource;
import rabbit.io.BufferHandle;
import rabbit.proxy.Connection;
import rabbit.proxy.TrafficLoggerHandler;
import rabbit.handler.converter.ExternalProcessConverter;
import rabbit.util.SProperties;

/** This image handler uses an external program to convert images.
 *  The default converter is the program "convert" from GraphicsMagick.
 *  Using graphicsmagick with "gm convert" also seems to work fine.
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ImageHandler extends ImageHandlerBase {
    private ExternalProcessConverter epc;

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

    @Override protected ImageConversionResult
    internalConvertImage (String entryName) throws IOException {
	long origSize = size;
	File origFile = new File (entryName);
	convertedFile = new File (entryName + ".c");
	File typeFile = new File (entryName + ".type");
	epc.convertImage (origFile, convertedFile);
	return new ImageConversionResult (origSize, convertedFile, typeFile);
    }

    @Override public void setup (SProperties prop) {
	epc = new ExternalProcessConverter (prop);
    }
}
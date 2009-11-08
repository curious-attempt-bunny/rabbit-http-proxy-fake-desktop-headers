package rabbit.handler;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
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
    private static final String STD_QUALITY = "0.1";
    private long maxImageSize;

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
			     int minSizeToConvert, long maxImageSize) {
	super (con, tlh, request, clientHandle, response, content,
	       mayCache, mayFilter, size, config, doConvert, minSizeToConvert);
	this.maxImageSize = maxImageSize;
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
				     getMinSizeToConvert (), maxImageSize);
    }

    private float getQuality () {
	String sq = getConfig ().getProperty ("quality", STD_QUALITY);
	return Float.parseFloat (sq);
    }

    private ImageWriter getImageWriter () throws IOException {
	Iterator<ImageWriter> iter = 
	    ImageIO.getImageWritersByFormatName ("jpeg");
	if (iter.hasNext()) 
	    return iter.next ();
	throw new IOException ("Failed to find jpeg writer");
    }

    private JPEGImageWriteParam getParams () {
	JPEGImageWriteParam iwparam = 
	    new JPEGImageWriteParam (Locale.getDefault ());
	iwparam.setCompressionMode (ImageWriteParam.MODE_EXPLICIT);
	iwparam.setCompressionQuality (getQuality ());
	return iwparam;
    }

    private BufferedImage getRGBImage (BufferedImage orig) {
	BufferedImage newImage = 
	    new BufferedImage (orig.getWidth (), orig.getHeight (), 
			       BufferedImage.TYPE_INT_RGB);
	try {
	    Graphics g2 = newImage.getGraphics (); 
	    try {
		g2.drawImage (orig, 0, 0, null);
	    } finally {
		g2.dispose ();
	    }
	} finally {
	    orig.flush ();
	}
	return newImage;
    }
    
    @Override protected ImageConversionResult 
    internalConvertImage (String entryName) throws IOException {
	long origSize = size;
	File input = new File (entryName);
	File output = new File (entryName + ".c");
	
	// TODO: check image size so that we can limit total memory usage
	BufferedImage origImage = getImage (input);
	if (origImage == null) {
	    return new ImageConversionResult (origSize, output, null);
	}
	try {
	    if (origImage.getType () == BufferedImage.TYPE_CUSTOM)
		origImage = getRGBImage (origImage);
	    ImageWriter writer = getImageWriter ();
	    try {
		ImageOutputStream ios = ImageIO.createImageOutputStream (output);
		try {
		    writer.setOutput(ios);
		    IIOImage iioimage = new IIOImage (origImage, null, null);
		    writer.write (null, iioimage, getParams ());
		} finally {
		    ios.close ();
		}
	    } finally {
		writer.dispose ();
	    }
	} finally {
	    origImage.flush ();
	    convertedFile = output;
	}
	return new ImageConversionResult (origSize, output, null);
    }

    private BufferedImage getImage (File input) throws IOException {
	ImageInputStream iis = ImageIO.createImageInputStream (input);
	try{
	    Iterator<ImageReader> readers = ImageIO.getImageReaders (iis);
	    if (!readers.hasNext ()) 
		throw new IOException ("Failed to find image reader: " + 
				       request.getRequestURI ());

	    ImageReader ir = readers.next ();
	    try {
		return getImage (ir, iis);
	    } finally {
		ir.dispose ();
	    }
	} finally {
	    iis.close ();
	}
    }

    private BufferedImage getImage (ImageReader ir, ImageInputStream iis) 
	throws IOException {
	ir.setInput (iis);
	// 4 bytes per pixels, we may need 2 images
	long size = ir.getWidth (0) * ir.getHeight (0) * 4 * 2;
	if (size > maxImageSize)
	    throw new IOException ("Image is too large, wont convert: " +
				   request.getRequestURI ());
	return ir.read (0);
    }

    @Override public void setup (SProperties prop) {
	super.setup (prop);
        Runtime rt = Runtime.getRuntime ();
        long max = rt.maxMemory ();
	maxImageSize = max / 4;
    }
}
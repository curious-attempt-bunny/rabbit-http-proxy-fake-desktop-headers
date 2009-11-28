package rabbit.handler.convert;

import java.io.File;
import java.io.IOException;

/** An image converter. 
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public interface ImageConverter {
    /** Convert an image */
    void convertImage (File from, File to, String info) throws IOException;
}

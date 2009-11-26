package rabbit.handler.converter;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import rabbit.handler.ImageConverter;
import rabbit.util.SProperties;

/** An image converter that runs an external program
 *  to do the actual conversion.
 */
public class ExternalProcessConverter implements ImageConverter {
    private static final String STD_CONVERT = "/usr/bin/gm";
    private static final String STD_CONVERT_ARGS =
    "conovert -quality 10 -flatten $filename +profile \"*\" jpeg:$filename.c";

    private SProperties cfg;
    private boolean canConvert = true;

    private final Logger logger = Logger.getLogger (getClass ().getName ());

    public void convertImage (File from, File to) throws IOException {
	String convert = cfg.getProperty ("convert", STD_CONVERT);
	String convargs = cfg.getProperty ("convertargs", STD_CONVERT_ARGS);
	File typeFile = null;
	int idx = 0;
	String entryName = from.getAbsolutePath ();
	while ((idx = convargs.indexOf ("$filename")) > -1) {
	    convargs = convargs.substring (0, idx) + entryName +
		convargs.substring (idx + "$filename".length());
	}
	String command = convert + " " + convargs;
	logger.fine ("ImageHandler running: '" + command + "'");
	Process ps = Runtime.getRuntime ().exec (command);
	try {
	    ps.waitFor ();
	    closeStreams (ps);
	    int exitValue = ps.exitValue ();
	    if (exitValue != 0) {
		logger.warning ("Bad conversion: " + entryName +
				", got exit value: " + exitValue);
		throw new IOException ("failed to convert image, " +
				       "exit value: " + exitValue);
	    }
	} catch (InterruptedException e) {
	    logger.warning ("Interupted during wait for: " +
			    entryName);
	}
    }

    public void closeStreams (Process ps) throws IOException {
	ps.getInputStream ().close ();
	ps.getOutputStream ().close ();
	ps.getErrorStream ().close ();
    }

    public ExternalProcessConverter (SProperties prop) {
	this.cfg = prop;
	if (prop != null) {
	    String conv = prop.getProperty ("convert", STD_CONVERT);
	    File f = new File (conv);
	    if (!f.exists () || !f.isFile ()) {
		logger.warning ("convert -" + conv +
				      "- not found, is your path correct?");
		canConvert = false;
	    }
	}
    }
}

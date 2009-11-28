package rabbit.handler.convert;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import rabbit.util.SProperties;

/** An image converter that runs an external program
 *  to do the actual conversion.
 */
public class ExternalProcessConverter implements ImageConverter {
    private static final String STD_CONVERT = "/usr/bin/gm";
    private static final String STD_CONVERT_ARGS =
    "conovert -quality 10 -flatten $filename +profile \"*\" jpeg:$filename.c";

    private final boolean canConvert;
    private final String convert;
    private final String convertArgs;
    private final Logger logger = Logger.getLogger (getClass ().getName ());

    public ExternalProcessConverter (SProperties props) {
	convert = props.getProperty ("convert", STD_CONVERT);
	convertArgs = props.getProperty ("convertargs", STD_CONVERT_ARGS);
	String conv = props.getProperty ("convert", STD_CONVERT);
	File f = new File (conv);
	if (!f.exists () || !f.isFile ()) {
	    logger.warning ("convert -" + conv +
			    "- not found, is your path correct?");
	    canConvert = false;
	} else {
	    canConvert = true;
	}
    }

    public void convertImage (File from, File to, String info)
	throws IOException {
	File typeFile = null;
	int idx = 0;
	String entryName = from.getAbsolutePath ();
	String convargs = "";
	while ((idx = convertArgs.indexOf ("$filename")) > -1) {
	    convargs = convertArgs.substring (0, idx) + entryName +
		convertArgs.substring (idx + "$filename".length());
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
				       "exit value: " + exitValue + 
				       ", info: " + info);
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
}

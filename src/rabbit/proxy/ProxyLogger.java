package rabbit.proxy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import rabbit.util.SProperties;

/** A class to handle proxy logging. 
 * 
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ProxyLogger implements ConnectionLogger {

    /** The current config */
    private SProperties config;

    /** Output for accesses */
    private LogWriter accessLog = new LogWriter (System.out, true);

    /** monitor for access log. */
    private Object accessMonitor = new Object ();

    /** The format we write dates on. */
    private SimpleDateFormat sdf = 
    new SimpleDateFormat ("dd/MMM/yyyy:HH:mm:ss 'GMT'");

    /** The monitor for sdf. */
    private Object sdfMonitor = new Object ();

    /** The distance to GMT in milis. */
    private long offset;    

    private final Logger logger = Logger.getLogger (getClass ().getName ());

    /** Create a new ProxyLogger. */
    public ProxyLogger () {
	TimeZone tz = sdf.getTimeZone ();
	GregorianCalendar gc = new GregorianCalendar ();
	gc.setTime (new Date ());
	offset = tz.getOffset (gc.get (Calendar.ERA),
			       gc.get (Calendar.YEAR),
			       gc.get (Calendar.MONTH),
			       gc.get (Calendar.DAY_OF_MONTH),
			       gc.get (Calendar.DAY_OF_WEEK),
			       gc.get (Calendar.MILLISECOND));	
    }

    /** Get the distance to GMT in millis 
     */
    public long getOffset () {
	return offset;
    }

    public void setup (SProperties config) {
	if (config == null) // just default if we got no config.
	    config = new SProperties ();
	this.config = config;
	accessLog = setupLog (config, accessLog, accessMonitor, "accesslog", 
			      "logs/access_log", System.out);
    }
    
    /** Configure the error log.
     */
    private LogWriter setupLog (SProperties config, LogWriter currentLogger, 
				Object monitor, String entry, 
				String defaultLog, PrintStream defaultStream) {
	String log = config.getProperty (entry, defaultLog);
	synchronized (monitor) {
	    try {	    
		closeLog (currentLogger);
		if (!log.equals ("")) {
		    File f = new File (log);
		    File p = new File (f.getParent ());
		    if (!p.exists ())
			if (!p.mkdirs ()) {
			    String err = "faile to create directories: " + 
				p.getAbsolutePath ();
			    throw new IOException (err);
			}
		    return new LogWriter (new FileWriter (log, true), true);
		}
		return new LogWriter (defaultStream, true);
	    } catch (IOException e) {
		logger.log (Level.SEVERE, 
			    "Could not create log on '" + log + "'", 
			    e);
	    }
	    return new LogWriter (defaultStream, true);
	}
    }
    
    public void rotateLogs () {
	logger.info ("Log rotation requested.");
	Date d = new Date ();
	SimpleDateFormat lf = new SimpleDateFormat ("yyyy-MM-dd");
	String date = lf.format (d);	
	accessLog = rotateLog (config, accessLog, accessMonitor, "accesslog", 
			       "logs/access_log", System.out, date);	
    }

    private LogWriter rotateLog (SProperties config, LogWriter w, 
				 Object monitor, String entry, 
				 String defaultLog, 
				 PrintStream defaultStream, String date) {
	synchronized (monitor) {
	    if (w != null && !w.isSystemWriter ()) {
		closeLog (w);
		String log = config.getProperty (entry, defaultLog);
		File f = new File (log);
		File fn = new File (log + "-" + date);	    
		if (f.renameTo (fn))
		    return setupLog (config, w, monitor, entry, 
				     defaultLog, defaultStream);
		logger.warning ("Failed to rotate log!");
	    }
	    return w;
	}
    }

    /** Flush and close the logfile given.
     * @param w the logfile to close.
     */
    private void closeLog (LogWriter w) {
	if (w != null && !w.isSystemWriter ()) {
	    w.flush ();
	    w.close ();
	}
    }
    
    /** Close down this logger. Will set the access and error logs to console.
     */
    public void close () {
	synchronized (accessMonitor) {
	    accessLog.flush ();
	    accessLog.close ();
	    accessLog = new LogWriter (System.out, true);
	}
    }

    public void logConnection (Connection con) {
	StringBuilder sb = new StringBuilder ();
	Socket s = con.getChannel ().socket (); 
	if (s != null) {
	    InetAddress ia = s.getInetAddress (); 
	    if (ia != null)
		sb.append (ia.getHostAddress());
	    else 
		sb.append ("????");
	}
	sb.append (" - ");
	sb.append ((con.getUserName () != null ? con.getUserName () : "-"));
	sb.append (" ");
	long now = System.currentTimeMillis ();
	Date d = new Date (now - offset);
	synchronized (sdfMonitor) {
	    sb.append (sdf.format (d));
	}
	sb.append (" \"");
	sb.append (con.getRequestLine ());
	sb.append ("\" ");
	sb.append (con.getStatusCode ());
	sb.append (" ");
	sb.append (con.getContentLength ());
	sb.append (" ");
	sb.append (con.getId ().toString ());
	sb.append (" ");
	sb.append ((con.getExtraInfo () != null ? con.getExtraInfo () : ""));
	synchronized (accessMonitor) {
	    accessLog.println (sb.toString ());
	}
    }
}

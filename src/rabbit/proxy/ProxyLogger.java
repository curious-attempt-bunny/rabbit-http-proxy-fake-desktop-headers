package rabbit.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import rabbit.util.SProperties;

/** A class to handle proxy logging. 
 * 
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class ProxyLogger implements ConnectionLogger {

    /** Output for accesses */
    private Logger accessLog;

    /** The format we write dates on. */
    private SimpleDateFormat sdf = 
    new SimpleDateFormat ("dd/MMM/yyyy:HH:mm:ss 'GMT'");

    /** The monitor for sdf. */
    private Object sdfMonitor = new Object ();

    /** The distance to GMT in milis. */
    private long offset;    

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

    public static class LoggerAndHandler {
	public final Handler handler;
	public final Logger logger;
	
	public LoggerAndHandler (Handler handler, Logger logger) {
	    this.handler = handler;
	    this.logger = logger;
	}
    }

    public static LoggerAndHandler getLogger (SProperties config, 
					      String prefix,
					      String logDomain) 
	throws IOException {
	String log = config.get (prefix + "_log");
	String sl = config.get (prefix + "_size_limit");
	sl = sl == null ? "1" : sl.trim ();
	int limit = Integer.parseInt (sl) * 1024 * 1024;
	int numFiles = Integer.parseInt (config.get (prefix + "_num_files"), 10);
	sl = config.get (prefix + "_log_level");
	sl = sl != null ? sl : "INFO";
	Level level = Level.parse (sl);

	FileHandler fh = new FileHandler (log, limit, numFiles, true);
	fh.setFormatter (new SimpleFormatter ());
	Logger logger = Logger.getLogger(logDomain);
	logger.setLevel (level);
	logger.addHandler (fh);
	logger.setUseParentHandlers (false);
	
	return new LoggerAndHandler (fh, logger);
    }

    public void setup (SProperties config) throws IOException {
	LoggerAndHandler lah = getLogger (config, "access", "rabbit.access");
	accessLog = lah.logger;
	lah.handler.setFormatter (new AccessFormatter ());
    }

    private static class AccessFormatter extends Formatter {
	@Override public String format (LogRecord record) {
	    return record.getMessage () + "\n";
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

	accessLog.log (Level.INFO, sb.toString ());
    }
}

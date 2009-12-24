package rabbit.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import rabbit.handler.HandlerFactory;
import rabbit.util.Config;
import rabbit.util.SProperties;

/** A class to handle mime type handler factories.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class HandlerFactoryHandler {
    private final List<HandlerInfo> handlers;
    private final List<HandlerInfo> cacheHandlers;
    private final Logger logger = Logger.getLogger (getClass ().getName ());
    
    public HandlerFactoryHandler (SProperties handlersProps, 
				  SProperties cacheHandlersProps, 
				  Config config) {
	handlers = loadHandlers (handlersProps, config);
	cacheHandlers = loadHandlers (cacheHandlersProps, config);
    }

    private static class HandlerInfo {
	public final String mime;
	public final Pattern pattern;
	public final HandlerFactory factory;

	public HandlerInfo (String mime, HandlerFactory factory) {
	    this.mime = mime;
	    this.pattern = Pattern.compile (mime, Pattern.CASE_INSENSITIVE);
	    this.factory = factory;
	}

	public boolean accept (String mime) {
	    Matcher m = pattern.matcher (mime);
	    return m.matches ();
	}

	@Override public String toString () {
	    return getClass ().getSimpleName () + "{" + mime + ", " +
		factory + "}";
	}
    }

    /** load a set of handlers.
     * @param section the section in the config file.
     * @param log the Logger to write errors/warnings to.
     * @return a Map with mimetypes as keys and Handlers as values.
     */
    protected List<HandlerInfo> loadHandlers (SProperties handlersProps,
					      Config config) {
	List<HandlerInfo> hhandlers = new ArrayList<HandlerInfo> ();
	if (handlersProps == null)
	    return hhandlers;
	for (String handler : handlersProps.keySet ()) {
	    HandlerFactory hf;
	    String id = handlersProps.getProperty (handler).trim ();
	    hf = setupHandler (id, config, handler);
	    hhandlers.add (new HandlerInfo (handler, hf));
	}
	return hhandlers;
    }

    private HandlerFactory setupHandler (String id, Config config, 
					 String handler) {
	String className = id;
	HandlerFactory hf = null;
	try {
	    int i = id.indexOf ('*');
	    if (i >= 0)
		className = id.substring (0, i);
	    Class<? extends HandlerFactory> cls = 
		Class.forName (className).asSubclass (HandlerFactory.class);
	    hf = cls.newInstance ();
	    hf.setup (config.getProperties (id));
	} catch (ClassNotFoundException ex) {
	    logger.log (Level.WARNING, 
		       "Could not load class: '" + className
		       + "' for handlerfactory '" + handler + "'", 
		       ex);
	} catch (InstantiationException ie) {
	    logger.log (Level.WARNING, 
			"Could not instanciate factory class: '" + 
			className + "' for handler '" + handler + "'",
			ie);
	} catch (IllegalAccessException iae) {
	    logger.log (Level.WARNING, 
			"Could not instanciate factory class: '" + 
			className + "' for handler '" + handler + "'",
			iae);
	}
	return hf;
    }

    HandlerFactory getHandlerFactory (String mime) {
	for (HandlerInfo hi : handlers) {
	    if (hi.accept (mime))
		return hi.factory;
	}
	return null;
    }

    HandlerFactory getCacheHandlerFactory (String mime) {
	for (HandlerInfo hi : cacheHandlers) {
	    if (hi.accept (mime))
		return hi.factory;
	}
	return null;
    }
}

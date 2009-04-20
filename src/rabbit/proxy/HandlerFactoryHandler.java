package rabbit.proxy;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import rabbit.handler.HandlerFactory;
import rabbit.util.Config;
import rabbit.util.SProperties;

/** A class to handle mime type handler factories.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class HandlerFactoryHandler {
    private final Map<String, HandlerFactory> handlers;
    private final Map<String, HandlerFactory> cacheHandlers;
    private final Logger logger = Logger.getLogger (getClass ().getName ());
    
    public HandlerFactoryHandler (SProperties handlersProps, 
				  SProperties cacheHandlersProps, 
				  Config config) {
	handlers = loadHandlers (handlersProps, config);
	cacheHandlers = loadHandlers (cacheHandlersProps, config);
    }

    /** load a set of handlers.
     * @param section the section in the config file.
     * @param log the Logger to write errors/warnings to.
     * @return a Map with mimetypes as keys and Handlers as values.
     */
    protected Map<String, HandlerFactory> 
	loadHandlers (SProperties handlersProps, Config config) {
	Map<String, HandlerFactory> hhandlers = 
	    new HashMap<String, HandlerFactory> ();
	if (handlersProps == null)
	    return hhandlers;
	for (String handler : handlersProps.keySet ()) {
	    HandlerFactory hf;
	    String id = handlersProps.getProperty (handler).trim ();
	    // simple regexp like expansion,
	    // first '?' char indicates optional prev char
	    int i = handler.indexOf ('?');
	    if (i <= 0) {
		// no '?' found, or it is the first char
		hf = setupHandler (id, config, handler);
		hhandlers.put (handler, hf);
	    } else {
		// remove '?'
		handler = handler.substring (0, i) + handler.substring (i + 1);
		hf = setupHandler (id, config, handler);
		hhandlers.put (handler, hf);
		// remove the optional char
		String handler2 = 
		    handler.substring (0, i - 1) + handler.substring (i);
		hf = setupHandler (id, config, handler2);
		hhandlers.put (handler2, hf);
	    }
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
	return handlers.get (mime);
    }

    HandlerFactory getCacheHandlerFactory (String mime) {
	return cacheHandlers.get (mime);
    }
}

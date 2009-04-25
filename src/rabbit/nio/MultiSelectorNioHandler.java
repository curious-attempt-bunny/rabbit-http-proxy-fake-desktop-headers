package rabbit.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** An implementation of NioHandler that runs several
 *  selector threads.
 */
public class MultiSelectorNioHandler implements NioHandler {
     /** The executor service. */
    private final ExecutorService executorService;
    private final List<SingleSelectorRunner> selectorRunners;
    private final Logger logger = Logger.getLogger (getClass ().getName ());
    private AtomicInteger nextIndex = new AtomicInteger (0);

    public MultiSelectorNioHandler (ExecutorService executorService,
				    int numSelectors)
	throws IOException {
	this.executorService = executorService;
	selectorRunners = new ArrayList<SingleSelectorRunner> (numSelectors);
	for (int i = 0; i < numSelectors; i++)
	    selectorRunners.add (new SingleSelectorRunner (executorService));
    }

    public void start () {
	for (SingleSelectorRunner ssr : selectorRunners)
	    ssr.start ();
    }

    public void shutdown () {
	Thread t = new Thread (new Runnable () {
		public void run () {
		    executorService.shutdown ();
		    for (SingleSelectorRunner ssr : selectorRunners)
			ssr.shutdown ();
		}
	    });
	t.start ();
    }

    public Long getDefaultTimeout () {
	return new Long (System.currentTimeMillis () + 15000);
    }

    public void runThreadTask (Runnable r) {
	executorService.execute (r);
    }

    private SingleSelectorRunner getSelectorRunner () {
	int index = nextIndex.getAndIncrement ();
	index %= selectorRunners.size ();
	return selectorRunners.get (index);
    }

    /** Run a task on one of the selector threads.
     *  The task will be run sometime in the future.
     * @param sr the task to run on the main thread.
     */
    private void runSelectorTask (SelectableChannel channel, 
				  SelectorRunnable sr) {
	for (SingleSelectorRunner ssr : selectorRunners) {
	    if (ssr.handlesChannel (channel)) {
		ssr.runSelectorTask (sr);
		return;
	    }
	}
	SingleSelectorRunner ssr = getSelectorRunner ();
	ssr.runSelectorTask (sr);
    }

    public void waitForRead (final SelectableChannel channel,
			     final ReadHandler handler) {
	if (logger.isLoggable (Level.FINEST))
	    logger.fine ("Waiting for read for: channel: " + channel +
			 ", handler: " + handler);
	runSelectorTask (channel, new SelectorRunnable () {
		public void run (SingleSelectorRunner ssr) throws IOException {
		    ssr.waitForRead (channel, handler);
		}
	    });
    }

    public void waitForWrite (final SelectableChannel channel,
			      final WriteHandler handler) {
	if (logger.isLoggable (Level.FINEST))
	    logger.fine ("Waiting for write for: channel: " + channel +
			 ", handler: " + handler);
	runSelectorTask (channel, new SelectorRunnable () {
		public void run (SingleSelectorRunner ssr) throws IOException {
		    ssr.waitForWrite (channel, handler);
		}
	    });
    }

    public void waitForAccept (final SelectableChannel channel,
			       final AcceptHandler handler) {
	if (logger.isLoggable (Level.FINEST))
	    logger.fine ("Waiting for accept for: channel: " + channel +
			 ", handler: " + handler);
	runSelectorTask (channel, new SelectorRunnable () {
		public void run (SingleSelectorRunner ssr) throws IOException {
		    ssr.waitForAccept (channel, handler);
		}
	    });
    }

    public void waitForConnect (final SelectableChannel channel,
				final ConnectHandler handler) {
	runSelectorTask (channel, new SelectorRunnable () {
		public void run (SingleSelectorRunner ssr) throws IOException {
		    ssr.waitForConnect (channel, handler);
		}
	    });
    }

    public void cancel (final SelectableChannel channel,
			final SocketChannelHandler handler) {
	for (SingleSelectorRunner sr : selectorRunners) {
	    sr.runSelectorTask (new SelectorRunnable () {
		    public void run (SingleSelectorRunner ssr) {
			ssr.cancel (channel, handler);
		    }
		});
	}
    }

    public void close (final SelectableChannel channel) {
	for (SingleSelectorRunner sr : selectorRunners) {
	    sr.runSelectorTask (new SelectorRunnable () {
		    public void run (SingleSelectorRunner ssr) {
			ssr.close (channel);
		    }
		});
	}
    }

    public void visitSelectors (SelectorVisitor visitor) {
	// TODO: do we need to run on the respective threads?
	for (SingleSelectorRunner sr : selectorRunners)
	    sr.visit (visitor);
	visitor.end ();
    }
}

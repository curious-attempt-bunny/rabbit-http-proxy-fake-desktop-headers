package rabbit.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/** An implementation of NioHandler that runs several 
 *  selector threads. 
 */
public class MultiSelectorNioHandler implements NioHandler {
     /** The executor service. */
    private final ExecutorService executorService;
    private final List<SingleSelectorRunner> selectorRunners;
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
	for (SingleSelectorRunner ssr : selectorRunners)
	    ssr.shutdown ();
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
    private void runSelectorTask (SelectorRunnable sr) {
	SingleSelectorRunner ssr = getSelectorRunner ();
	ssr.runSelectorTask (sr);
    }
    
    public void waitForRead (final SelectableChannel channel, 
			     final ReadHandler handler) {
	runSelectorTask (new SelectorRunnable () {
		public void run (SingleSelectorRunner ssr) throws IOException {
		    ssr.waitForRead (channel, handler);
		}
	    });
    }

    public void waitForWrite (final SelectableChannel channel, 
			      final WriteHandler handler) {
	runSelectorTask (new SelectorRunnable () {
		public void run (SingleSelectorRunner ssr) throws IOException {
		    ssr.waitForWrite (channel, handler);
		}
	    });
    }

    public void waitForAccept (final SelectableChannel channel, 
			       final AcceptHandler handler) {
	runSelectorTask (new SelectorRunnable () {
		public void run (SingleSelectorRunner ssr) throws IOException {
		    ssr.waitForAccept (channel, handler);
		}
	    });
    }

    public void waitForConnect (final SelectableChannel channel, 
				final ConnectHandler handler) {
	runSelectorTask (new SelectorRunnable () {
		public void run (SingleSelectorRunner ssr) throws IOException {
		    ssr.waitForConnect (channel, handler);
		}
	    });
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
}

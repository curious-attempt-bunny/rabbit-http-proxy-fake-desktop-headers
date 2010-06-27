package rabbit.client;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import rabbit.http.HttpHeader;

/** A helper class that shuts down the clientBase when all requests have finished.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class CountingClientBaseStopper {
    private final AtomicInteger outstandingRequests = new AtomicInteger ();
    private final ClientBase clientBase;

    public CountingClientBaseStopper (ClientBase clientBase) {
	this.clientBase = clientBase;
    }

    public void sendRequest (HttpHeader request, ClientListener listener) 
	throws IOException {
	outstandingRequests.incrementAndGet ();
	clientBase.sendRequest (request, listener);
    }

    public void requestDone () {
	int outstanding = outstandingRequests.decrementAndGet ();
	if (outstanding == 0)
	    clientBase.shutdown ();	
    }
}
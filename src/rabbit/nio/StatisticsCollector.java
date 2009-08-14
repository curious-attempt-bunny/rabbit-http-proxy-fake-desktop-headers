package rabbit.nio;

/** A class that executes one task and gathers information about
 *  the time spent and the success status of the task. 
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class StatisticsCollector implements Runnable {
    private final StatisticsHolder stats;
    private final Runnable realTask;
    private final TaskIdentifier ti;

    public StatisticsCollector (StatisticsHolder stats, 
				Runnable realTask, 
				TaskIdentifier ti) {
	this.stats = stats;
	this.realTask = realTask;
	this.ti = ti;
    }

    public void run () {
	stats.changeTaskStatusToRunning (ti);
	long started = System.currentTimeMillis ();
	boolean wasOk = false;
	try {
	    realTask.run ();
	    wasOk = true;
	} finally {
	    long ended = System.currentTimeMillis ();
	    long diff = ended - started;
	    stats.changeTaskStatusToFinished (ti, wasOk, diff);
	}
    }
}

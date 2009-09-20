package rabbit.nio.statistics;

/** Information about total time spent on a group of tasks.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class TotalTimeSpent {

    private long successful = 0;
    private long failures = 0;
    private long totalMillis = 0;

    /** Update this information with data from the newly completed task. 
     */
    public void update (CompletionEntry ce) {
	if (ce.wasOk)
	    successful++;
	else
	    failures++;
	totalMillis += ce.timeSpent;
    }

    /** Get the number of successfully completed jobs */
    public long getSuccessful () {
	return successful;
    }

    /** Get the number of failed jobs */
    public long getFailures () {
	return failures;
    }

    /** Get the total time spent doing this kind of task */
    public long getTotalMillis () {
	return totalMillis;
    }
}
    

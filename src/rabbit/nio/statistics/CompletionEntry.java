package rabbit.nio.statistics;

import rabbit.nio.TaskIdentifier;

/** Information about a completed task.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public final class CompletionEntry {
    /** The identifier of the task that has been completed. */
    public final TaskIdentifier ti;
    /** The status of the completed job. */
    public final boolean wasOk;
    /** The number of millis spent on the task. */
    public final long timeSpent;

    public CompletionEntry (TaskIdentifier ti, 
			    boolean wasOk, 
			    long timeSpent) {
	this.ti = ti;
	this.wasOk = wasOk;
	this.timeSpent = timeSpent;
    }
}

package rabbit.nio.statistics;

import rabbit.nio.TaskIdentifier;

/** Information about a completed task.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public final class CompletionEntry {
    public final TaskIdentifier ti;
    public final boolean wasOk;
    public final long timeSpent;

    public CompletionEntry (TaskIdentifier ti, 
			    boolean wasOk, 
			    long timeSpent) {
	this.ti = ti;
	this.wasOk = wasOk;
	this.timeSpent = timeSpent;
    }
}

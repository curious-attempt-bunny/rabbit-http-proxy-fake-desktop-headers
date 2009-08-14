package rabbit.nio;

/** A class that executes one task and gathers information about
 *  the time spent and the success status of the task. 
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class StatisticsHolder {
    public void addPendingTask (TaskIdentifier ti) {

    }

    public void changeTaskStatusToRunning (TaskIdentifier ti) {

    }

    public void changeTaskStatusToFinished (TaskIdentifier ti, 
					    boolean wasOk, 
					    long timeSpent) {

    }
}
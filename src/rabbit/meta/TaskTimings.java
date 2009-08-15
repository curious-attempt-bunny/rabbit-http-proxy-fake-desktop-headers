package rabbit.meta;

import java.util.List;
import java.util.Map;
import rabbit.nio.NioHandler;
import rabbit.nio.StatisticsHolder;
import rabbit.nio.TaskIdentifier;
import rabbit.proxy.HtmlPage;

/** A page that shows the currently open web connections.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class TaskTimings extends BaseMetaHandler {
    @Override protected String getPageHeader () {
	return "Task Timing Information";
    }
  
    /** Add the page information */
    @Override protected PageCompletion addPageInformation (StringBuilder sb) {
	addStatus (sb);
	return PageCompletion.PAGE_DONE;
    }

    private void addStatus (StringBuilder sb) {
	NioHandler nio = con.getNioHandler ();
	StatisticsHolder stats = nio.getTimingStatistics ();
	
	Map<String, List<TaskIdentifier>> pending = stats.getPendingTasks ();
	sb.append ("Pending tasks");
	sb.append (HtmlPage.getTableHeader (100, 1));
	sb.append (HtmlPage.getTableTopicRow ());
	sb.append ("<th width=\"30%\">Group</th>");
	sb.append ("<th width=\"70%\">Information</th>\n");
	sb.append ("</table><br>\n");

	for (Map.Entry<String, List<TaskIdentifier>> me : pending.entrySet ()) {
	    String id = me.getKey ();
	    for (TaskIdentifier ti : me.getValue ()) {
		sb.append ("<tr><td>" + ti.getGroupId () + "</td><td>" + 
			   ti.getDescription () + "</td></tr>\n");
	    }
	}
    }
}

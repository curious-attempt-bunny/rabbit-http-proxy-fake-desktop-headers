package rabbit.proxy;

/** A HttpGeneratorFactory that creates StandardResponseHeaders 
 *  instances.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class StandardHttpGeneratorFactory implements HttpGeneratorFactory {
    public HttpGenerator create (String identity, Connection con) {
	return new StandardResponseHeaders (identity, con);
    }
}
package rabbit.filter.authenticate;

import java.net.SocketAddress;

/** Information about an authenticated user.
 */
public class AuthUserInfo {
    private final String token;
    private final long timeout;
    private final SocketAddress sa;

    public AuthUserInfo (String token, long timeout, SocketAddress sa) {
	this.token = token;
	this.timeout = timeout;
	this.sa = sa;
    }

    public boolean stillValid () {
	long now = System.currentTimeMillis ();
	return timeout > now;
    }

    public boolean correctToken (String token) {
	return token.equals (this.token);
    }

    public boolean correctSocketAddress (SocketAddress sa) {
	return this.sa.equals (sa);
    }
}
    

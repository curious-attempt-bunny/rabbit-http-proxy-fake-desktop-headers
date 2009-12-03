package rabbit.filter.authenticate;

import java.net.InetAddress;

/** Information about an authenticated user.
 */
public class AuthUserInfo {
    private final String token;
    private final long timeout;
    private final InetAddress sa;

    public AuthUserInfo (String token, long timeout, InetAddress sa) {
	this.token = token;
	this.timeout = timeout;
	this.sa = sa;
    }

    @Override public String toString () {
	return getClass ().getSimpleName () + "{token: " + token + 
	    ", timeout: " + timeout + ", socket: " + sa + "}";
    }

    public boolean stillValid () {
	long now = System.currentTimeMillis ();
	return timeout > now;
    }

    public boolean correctToken (String token) {
	return token.equals (this.token);
    }

    public boolean correctSocketAddress (InetAddress sa) {
	return this.sa.equals (sa);
    }
}
    

package rabbit.filter.authenticate;

import java.net.SocketAddress;

/** Information about an authenticated user.
 */
public class AuthUserInfo {
    private final String pwd;
    private final long timeout;
    private final SocketAddress sa;

    public AuthUserInfo (String pwd, long timeout, SocketAddress sa) {
	this.pwd = pwd;
	this.timeout = timeout;
	this.sa = sa;
    }

    public boolean stillValid () {
	long now = System.currentTimeMillis ();
	return timeout > now;
    }

    public boolean correctPassWord (String userPassword) {
	return userPassword.equals (pwd);
    }

    public boolean correctSocketAddress (SocketAddress sa) {
	return this.sa.equals (sa);
    }
}
    

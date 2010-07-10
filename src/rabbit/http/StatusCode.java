package rabbit.http;

/** The http response codes.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public enum StatusCode {
    // 10.1  Informational 1xx
    _100 (100, "Continue"),
    _101 (101, "Switching Protocols"),
    // 10.2  Successful 2xx
    _200 (200, "OK"),
    _201 (201, "Created"),
    _202 (202, "Accepted"),
    _203 (203, "Non-Authoritative Information"),
    _204 (204, "No Content"),
    _205 (205, "Reset Content"),
    _206 (206, "Partial Content"),
    // 10.3  Redirection 3xx
    _300 (300, "Multiple Choices"),
    _301 (301, "Moved Permanently"),
    _302 (302, "Found"),
    _303 (303, "See Other"),
    _304 (304, "Not Modified"),
    _305 (305, "Use Proxy"),
    _306 (306, "(Unused)"),
    _307 (307, "Temporary Redirect"),
    // 10.4  Client Error 4xx
    _400 (400, "Bad Request"),
    _401 (401, "Unauthorized"),
    _402 (402, "Payment Required"),
    _403 (403, "Forbidden"),
    _404 (404, "Not Found"),
    _405 (405, "Method Not Allowed"),
    _406 (406, "Not Acceptable"),
    _407 (407, "Proxy Authentication Required"),
    _408 (408, "Request Timeout"),
    _409 (409, "Conflict"),
    _410 (410, "Gone"),
    _411 (411, "Length Required"),
    _412 (412, "Precondition Failed"),
    _413 (413, "Request Entity Too Large"),
    _414 (414, "Request-URI Too Long"),
    _415 (415, "Unsupported Media Type"),
    _416 (416, "Requested Range Not Satisfiable"),
    _417 (417, "Expectation Failed"),
    // 10.5  Server Error 5xx
    _500 (500, "Internal Server Error"),
    _501 (501, "Not Implemented"),
    _502 (502, "Bad Gateway"),
    _503 (503, "Service Unavailable"),
    _504 (504, "Gateway Timeout"),
    _505 (505, "HTTP Version Not Supported");

    private final int code;
    private final String description;

    private StatusCode (int code, String description) {
	this.code = code;
	this.description = description;
    }

    /** Get the numeric value of the status code
     * @return the status code
     */
    public int getCode () {
	return code;
    }

    /** Get the human readable description of this status code.
     * @return the description
     */
    public String getDescription () {
	return description;
    }

    /** Get a http response line using this status code
     * @param httpVersion the HTTP version to use
     * @return the formatted status line
     */
    public String getStatusLine (String httpVersion) {
	return httpVersion + " " + getCode () + " " + getDescription ();
    }
}
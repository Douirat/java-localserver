package http.request;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.nio.charset.StandardCharsets;

public class Request implements Requesting {

    private String method;
    private String path;
    private String version;



    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // respect the case insensitive nature of the headers.
    private final Map<String, String> queryParameters = new HashMap<>();
    private final Map<String, String> pathVariables = new HashMap<>();
    private  final Map<String, String> cookies = new HashMap<>();

    // In the case of a static file i will need to add the

    private byte[] body;

    public Request() {

    }

    // --- controlled parsing API ---
    public void setRequestLine(String[] requestLine) {

        this.method = requestLine[0];
        this.path = requestLine[1];
        this.version = requestLine[2];

        if (this.path.contains("?")) {

            int ind = this.path.indexOf("?");

            String query = this.path.substring(ind + 1);
            this.path = this.path.substring(0, ind);

            String[] queryList = query.split("&");

            for (String q : queryList) {
                String[] qs = q.split("=");

                String key = qs[0];
                String value = qs.length > 1 ? qs[1] : "";

                this.queryParameters.put(key, value);
            }
        }
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public void addQueryParam(String key, String value) {
        queryParameters.put(key, value);
    }

    public void addCookie(String key, String value) {
        this.cookies.put(key, value);
    }

    // --- GETTERS ---
    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Important: expose read-only view to avoid external mutation
     */
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public Map<String, String> getQueryParameters() {
        return Collections.unmodifiableMap(queryParameters);
    }

    // optional convenience method.
    public String getHeader(String key) {
        return headers.get(key);
    }

    // path variables are set by the router when matching dynamic routes (e.g., /api/users/{id}).
    public void addPathVariable(String key, String value) {
        pathVariables.put(key, value);
    }

    public Map<String, String> getPathVariables() {
        return Collections.unmodifiableMap(pathVariables);
    }

    // Body comes after the header is handled.
    public void setBody(byte[] body) {
        this.body = body;
    }

    // return the cookies object, which contains all cookie attributes (name, value, domain, path, expires, secure, httpOnly, sameSite).
    public Map<String, String> getCookies() {
        return Collections.unmodifiableMap(cookies);
    }

    // get a specific cookie value by name
    public String getCookie(String name) {
        return cookies.get(name);
    }


    public byte[] getBody() {
        return body;
    }

    @Override
public String toString() {
    StringBuilder sb = new StringBuilder();

    // Request-line: METHOD SP request-target SP HTTP-version CRLF
    sb.append(method).append(' ')
      .append(buildRequestTarget()).append(' ')
      .append(version).append("\r\n");

    // Header fields: "Name: value" CRLF, one per header
    for (Map.Entry<String, String> entry : headers.entrySet()) {
        sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
    }

    // Cookies travel as ONE "Cookie" header, not separate fields (RFC 6265)
    if (!cookies.isEmpty()) {
        sb.append("Cookie: ").append(buildCookieHeader()).append("\r\n");
    }

    // Blank line marks end of headers
    sb.append("\r\n");

    // Message body, if any
    if (body != null && body.length > 0) {
        sb.append(new String(body, StandardCharsets.UTF_8));
    }

    return sb.toString();
}

private String buildRequestTarget() {
    if (queryParameters.isEmpty()) {
        return path;
    }
    StringBuilder target = new StringBuilder(path).append('?');
    boolean first = true;
    for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
        if (!first) target.append('&');
        target.append(entry.getKey()).append('=').append(entry.getValue());
        first = false;
    }
    return target.toString();
}

private String buildCookieHeader() {
    StringBuilder cookieHeader = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> entry : cookies.entrySet()) {
        if (!first) cookieHeader.append("; ");
        cookieHeader.append(entry.getKey()).append('=').append(entry.getValue());
        first = false;
    }
    return cookieHeader.toString();
}
}
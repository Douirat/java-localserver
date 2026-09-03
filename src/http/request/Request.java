package http.request;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import util.Session;
import util.SessionManager;

public class Request implements Requesting {

    private String method;
    private String path;
    private String version;

    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // respect the case
                                                                                              // insensitive nature of
                                                                                              // the headers.
    private final Map<String, String> queryParameters = new HashMap<>();
    private final Map<String, String> pathVariables = new HashMap<>();
    private final Map<String, String> cookies = new HashMap<>();
    private Session session;

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

    // path variables are set by the router when matching dynamic routes (e.g.,
    // /api/users/{id}).
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

    // return the cookies object, which contains all cookie attributes (name, value,
    // domain, path, expires, secure, httpOnly, sameSite).
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

    // Session management

    /**
     * Returns the existing session for this request if one has been resolved,
     * or null if there is none. Does NOT create a new session.
     */
    @Override
    public Session getSession() {
        return getSession(false);
    }

    @Override
    public Session getSession(boolean create) {
        if (session != null && session.isValid()) {
            return session;
        }
        // Try to look up session via SESSIONID cookie.
        String sessionId = getCookie(SessionManager.DEFAULT_SESSION_COOKIE_NAME);
        if (sessionId != null) {
            session = SessionManager.getInstance().getSession(sessionId);
            if (session != null) {
                return session;
            }
        }
        if (create) {
            session = SessionManager.getInstance().createSession();
        }
        return session;
    }

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("{\n");
        sb.append("  \"method\": \"").append(method).append("\",\n");
        sb.append("  \"path\": \"").append(path).append("\",\n");
        sb.append("  \"version\": \"").append(version).append("\",\n");

        sb.append("  \"headers\": {\n");
        appendMap(sb, headers, 4);
        sb.append("  },\n");

        sb.append("  \"queryParameters\": {\n");
        appendMap(sb, queryParameters, 4);
        sb.append("  },\n");

        sb.append("  \"pathVariables\": {\n");
        appendMap(sb, pathVariables, 4);
        sb.append("  },\n");

        sb.append("  \"cookies\": {\n");
        appendMap(sb, cookies, 4);
        sb.append("  },\n");

        sb.append("  \"bodyLength\": ")
                .append(body == null ? 0 : body.length)
                .append("\n");

        sb.append("}");

        return sb.toString();
    }

    private void appendMap(
            StringBuilder sb,
            Map<String, String> map,
            int indentation) {

        boolean first = true;

        for (Map.Entry<String, String> entry : map.entrySet()) {

            if (!first) {
                sb.append(",\n");
            }

            sb.append(" ".repeat(indentation))
                    .append("\"")
                    .append(entry.getKey())
                    .append("\": \"")
                    .append(entry.getValue())
                    .append("\"");

            first = false;
        }

        if (!map.isEmpty()) {
            sb.append("\n");
        }
    }

    private String buildRequestTarget() {
        if (queryParameters.isEmpty()) {
            return path;
        }
        StringBuilder target = new StringBuilder(path).append('?');
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
            if (!first)
                target.append('&');
            target.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return target.toString();
    }

    private String buildCookieHeader() {
        StringBuilder cookieHeader = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (!first)
                cookieHeader.append("; ");
            cookieHeader.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return cookieHeader.toString();
    }

    public void debug() {
        System.out.println("request debugging");
        System.out.println(this.toString());
    }
}
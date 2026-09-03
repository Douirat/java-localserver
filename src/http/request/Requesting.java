package http.request;

import java.util.*;

public interface Requesting {
    String getMethod();
    String getPath();
    String getVersion();

    // headers, query parameters, path variables, and cookies:
    Map<String, String> getHeaders();
    Map<String, String> getQueryParameters();
    Map<String, String> getPathVariables();
    Map<String, String> getCookies();

    // setters:
    void setRequestLine(String[] requestLine);
    void addHeader(String key, String value);
    void addQueryParam(String key, String value);
    byte[] getBody();

    // session management:
    util.Session getSession();
    util.Session getSession(boolean create);
    void setSession(util.Session session);
}
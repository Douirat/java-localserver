package http.request;

import java.util.*;

public interface Requesting {
    String getMethod();
    String getPath();
    String getVersion();

    Map<String, String> getHeaders();
    Map<String, String> getQueryParameters();
    Map<String, String> getPathVariables();
    Map<String, String> getCookies();

    byte[] getBody();
}
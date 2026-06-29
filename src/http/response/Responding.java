package http.response;

import java.nio.channels.FileChannel;
import java.util.*;
import http.response.cookie.*;

public interface Responding {
    void setVersion(String version);
    void setStatus(int status);
    void setHeader(String key, String value);
    void addCookie(Cookie cookie);
    void setBody(Object body);
    void SetAsStatic();
    void setFileChannel(FileChannel channel);
    String getVersion();
    int getStatus();
    String getStatusReason();
    String getHeader(String key);
    List<Cookie> getCookies();
    Map<String, String> getHeaders();
    Object getBody();
}
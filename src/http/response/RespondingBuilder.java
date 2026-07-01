package http.response;
import http.response.cookie.*;
import http.response.responseBody.Body;

public interface RespondingBuilder<T extends RespondingBuilder<T>> {
    T setVersion(String version);
    T setStatus(int status);
    T setHeader(String key, String value);
    T addCookie(Cookie cookie);
    T setBody(Object body);
    T setBody(Body body);
    T setAsStatic();
    Response build();
}
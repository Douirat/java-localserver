public interface RespondingBuilder<T extends RespondingBuilder<T>> {
    T setVersion(String version);
    T setStatus(int status);
    T setHeader(String key, String value);
    T addCookie(Cookie cookie);
    T setBody(Object body);
    Response build();
}
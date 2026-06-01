public interface Requesting {
    setRequestLine(String[] requestLine);
    addHeader(String key, String value);
    addQueryParam(String key, String value);
    addCookie(String key, String value);
    String getMethod();
    String getPath();
    String getVersion();
    String getHeader(String key);
    String getQueryParam(String key);
    String getCookie(String key);
}
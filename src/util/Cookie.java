package util;

/**
 * Lightweight HTTP Cookie representation according to RFC 6265.
 */
public class Cookie {
    private final String name;
    private String value;
    private String path = "/";
    private int maxAge = -1; // -1 means session cookie (omits Max-Age)
    private boolean httpOnly = true;

    public Cookie(String name, String value) {
        this.name = (name != null) ? name.trim() : "";
        this.value = (value != null) ? value : "";
    }

    public static Cookie of(String name, String value) {
        return new Cookie(name, value);
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public Cookie setValue(String value) {
        this.value = (value != null) ? value : "";
        return this;
    }

    public String getPath() {
        return path;
    }

    public Cookie setPath(String path) {
        this.path = path;
        return this;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public Cookie setMaxAge(int maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public Cookie setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public String toHeaderValue() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(value);
        if (path != null && !path.isEmpty()) {
            sb.append("; Path=").append(path);
        }
        if (maxAge >= 0) {
            sb.append("; Max-Age=").append(maxAge);
        }
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toHeaderValue();
    }
}

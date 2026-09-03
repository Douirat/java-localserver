package util;

import java.util.Objects;


// Represents an HTTP Cookie with standard attributes according to RFC 6265.
 
public class Cookie {
    private final String name;
    private String value;
    private String domain;
    private String path = "/";
    private Long maxAge;
    private String expires;
    private boolean secure;
    private boolean httpOnly;
    private String sameSite; // "Strict", "Lax", "None"

    public Cookie(String name, String value) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Cookie name cannot be null or empty");
        }
        this.name = name.trim();
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

    public String getDomain() {
        return domain;
    }

    public Cookie setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getPath() {
        return path;
    }

    public Cookie setPath(String path) {
        this.path = path;
        return this;
    }

    public Long getMaxAge() {
        return maxAge;
    }

    public Cookie setMaxAge(long maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public String getExpires() {
        return expires;
    }

    public Cookie setExpires(String expires) {
        this.expires = expires;
        return this;
    }

    public boolean isSecure() {
        return secure;
    }

    public Cookie setSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public Cookie setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public String getSameSite() {
        return sameSite;
    }

    public Cookie setSameSite(String sameSite) {
        this.sameSite = sameSite;
        return this;
    }

    
    // Serializes this Cookie into the value format suitable for the "Set-Cookie" HTTP response header.
     
    public String toHeaderValue() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(value);

        if (path != null && !path.isEmpty()) {
            sb.append("; Path=").append(path);
        }
        if (domain != null && !domain.isEmpty()) {
            sb.append("; Domain=").append(domain);
        }
        if (maxAge != null) {
            sb.append("; Max-Age=").append(maxAge);
        }
        if (expires != null && !expires.isEmpty()) {
            sb.append("; Expires=").append(expires);
        }
        if (secure) {
            sb.append("; Secure");
        }
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        if (sameSite != null && !sameSite.isEmpty()) {
            sb.append("; SameSite=").append(sameSite);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toHeaderValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cookie cookie)) return false;
        return Objects.equals(name, cookie.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}

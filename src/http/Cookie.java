package http;

import java.util.Date;

/**
 * create an http cookie object to embed the server with a mechanism to imply the cookie header efficiently:
 * {
  "name": "sessionId",          -> cookie date.
  "value": "abc123",            -> cookie date.
  "domain": "myapp.com",        -> usable on all subdomains.
  "path": "/api",               -> only API routes.
  "expires": 1715003600,        ->  expires in "time in miliseconds"
  "secure": true,               -> HTTPS only.
  "httpOnly": true,             -> HttpOnly flag
  "sameSite": "Lax"             -> same site policy
}
*/
public class Cookie{
    private String name;
    private String value;
    private String domain;
    private String path;
    private Date expires;
    private boolean secure;
    private boolean httpOnly;
    private String sameSite;

    public Cookie(String name, String value) {
        this.name = name;
        this.value = value;
    }

    // Getters and setters for all fields
    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Date getExpires() {
        return expires;
    }

    public void setExpires(Date expires) {
        this.expires = expires;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }
}


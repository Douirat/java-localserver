package util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory session manager and session representation.
 */
public class Session {
    public static final String COOKIE_NAME = "SESSIONID";
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final String id;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private long lastAccessed;

    public Session(String id) {
        this.id = id;
        this.lastAccessed = System.currentTimeMillis();
    }

    // --- Static session management methods ---

    public static Session get(String id) {
        if (id == null) return null;
        Session s = SESSIONS.get(id);
        if (s != null) {
            if (System.currentTimeMillis() - s.lastAccessed > TIMEOUT_MS) {
                SESSIONS.remove(id);
                return null;
            }
            s.lastAccessed = System.currentTimeMillis();
        }
        return s;
    }

    public static Session create() {
        String id = UUID.randomUUID().toString();
        Session s = new Session(id);
        SESSIONS.put(id, s);
        return s;
    }

    public static void invalidate(String id) {
        if (id != null) SESSIONS.remove(id);
    }

    public static int getActiveCount() {
        long now = System.currentTimeMillis();
        SESSIONS.entrySet().removeIf(e -> now - e.getValue().lastAccessed > TIMEOUT_MS);
        return SESSIONS.size();
    }

    // --- Instance methods ---

    public String getId() {
        return id;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public void setAttribute(String name, Object value) {
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public boolean isValid() {
        return System.currentTimeMillis() - lastAccessed <= TIMEOUT_MS;
    }
}

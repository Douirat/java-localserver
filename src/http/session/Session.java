package http.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a user session with associated data.
 */
public class Session {

    private final String sessionId;
    private final Map<String, Object> data;
    private final long createdAt;
    private long lastAccessed;

    public Session(String sessionId) {
        this.sessionId = sessionId;
        this.data = new ConcurrentHashMap<>();
        this.createdAt = System.currentTimeMillis();
        this.lastAccessed = this.createdAt;
    }

    /**
     * Get the session ID.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Get the creation timestamp.
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Get the last accessed timestamp.
     */
    public long getLastAccessed() {
        return lastAccessed;
    }

    /**
     * Update the last accessed timestamp.
     */
    public void updateLastAccessed() {
        this.lastAccessed = System.currentTimeMillis();
    }

    /**
     * Set a session attribute.
     */
    public void setAttribute(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Get a session attribute.
     */
    public Object getAttribute(String key) {
        return data.get(key);
    }

    /**
     * Remove a session attribute.
     */
    public void removeAttribute(String key) {
        data.remove(key);
    }

    /**
     * Get all session attributes.
     */
    public Map<String, Object> getAttributes() {
        return new ConcurrentHashMap<>(data);
    }

    /**
     * Check if session has a specific attribute.
     */
    public boolean hasAttribute(String key) {
        return data.containsKey(key);
    }

    /**
     * Invalidate the session (clear all data).
     */
    public void invalidate() {
        data.clear();
    }
}

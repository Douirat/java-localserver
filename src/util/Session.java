package util;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


// Represents a client session stored on the server side.

public class Session {
    private final String id;
    private final long createdAt;
    private volatile long lastAccessedAt;
    private volatile long maxInactiveInterval; // timeout in seconds
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile boolean valid = true;

    public Session() {
        this(UUID.randomUUID().toString(), 1800); // 30 minutes default
    }

    public Session(String id, long maxInactiveInterval) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        this.id = id;
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedAt = this.createdAt;
        this.maxInactiveInterval = maxInactiveInterval;
    }

    public String getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void access() {
        this.lastAccessedAt = System.currentTimeMillis();
    }

    public long getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    public void setMaxInactiveInterval(long intervalSeconds) {
        this.maxInactiveInterval = intervalSeconds;
    }

    public Object getAttribute(String name) {
        checkValid();
        return attributes.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name, Class<T> type) {
        Object val = getAttribute(name);
        return (type.isInstance(val)) ? (T) val : null;
    }

    public void setAttribute(String name, Object value) {
        checkValid();
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
    }

    public void removeAttribute(String name) {
        checkValid();
        attributes.remove(name);
    }

    public Set<String> getAttributeNames() {
        checkValid();
        return Collections.unmodifiableSet(attributes.keySet());
    }

    public Map<String, Object> getAttributes() {
        checkValid();
        return Collections.unmodifiableMap(attributes);
    }

    public boolean isExpired() {
        if (!valid) {
            return true;
        }
        if (maxInactiveInterval <= 0) {
            return false; // zero or negative means session never expires
        }
        long idleTimeSeconds = (System.currentTimeMillis() - lastAccessedAt) / 1000;
        return idleTimeSeconds > maxInactiveInterval;
    }

    public boolean isValid() {
        return valid && !isExpired();
    }

    public void invalidate() {
        this.valid = false;
        this.attributes.clear();
    }

    private void checkValid() {
        if (!valid) {
            throw new IllegalStateException("Session " + id + " has already been invalidated");
        }
    }

    @Override
    public String toString() {
        return "Session{" +
                "id='" + id + '\'' +
                ", createdAt=" + createdAt +
                ", lastAccessedAt=" + lastAccessedAt +
                ", maxInactiveInterval=" + maxInactiveInterval +
                ", attributes=" + attributes +
                ", valid=" + valid +
                '}';
    }
}

package util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


// Manages active sessions, handles lookup, creation, expiration, and cleanup.

public class SessionManager {
    public static final String DEFAULT_SESSION_COOKIE_NAME = "SESSIONID";
    private static final SessionManager INSTANCE = new SessionManager();

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private long defaultMaxInactiveInterval = 1800; // 30 minutes in seconds

    private SessionManager() {
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public Session createSession() {
        String id = UUID.randomUUID().toString();
        Session session = new Session(id, defaultMaxInactiveInterval);
        sessions.put(id, session);
        return session;
    }

    public Session createSession(String id) {
        Session session = new Session(id, defaultMaxInactiveInterval);
        sessions.put(id, session);
        return session;
    }

    public Session getSession(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        Session session = sessions.get(id);
        if (session == null) {
            return null;
        }
        if (session.isExpired()) {
            sessions.remove(id);
            session.invalidate();
            return null;
        }
        session.access();
        return session;
    }

    public Session getOrCreateSession(String id) {
        Session session = getSession(id);
        if (session == null) {
            return createSession();
        }
        return session;
    }

    public void invalidateSession(String id) {
        if (id != null) {
            Session session = sessions.remove(id);
            if (session != null) {
                session.invalidate();
            }
        }
    }

    public void cleanExpiredSessions() {
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                entry.getValue().invalidate();
                return true;
            }
            return false;
        });
    }

    public int getActiveSessionCount() {
        cleanExpiredSessions();
        return sessions.size();
    }

    public long getDefaultMaxInactiveInterval() {
        return defaultMaxInactiveInterval;
    }

    public void setDefaultMaxInactiveInterval(long seconds) {
        this.defaultMaxInactiveInterval = seconds;
    }

    public void clear() {
        for (Session session : sessions.values()) {
            session.invalidate();
        }
        sessions.clear();
    }
}

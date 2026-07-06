package http.session;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages user sessions with secure session ID generation and automatic cleanup.
 */
public class SessionManager {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final ScheduledExecutorService cleanupExecutor;
    private final long sessionTimeoutMillis;

    public SessionManager() {
        this(30 * 60 * 1000); // 30 minutes default timeout
    }

    public SessionManager(long sessionTimeoutMillis) {
        this.sessionTimeoutMillis = sessionTimeoutMillis;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        
        // Schedule periodic cleanup of expired sessions
        this.cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredSessions,
            5, // initial delay 5 minutes
            5, // period 5 minutes
            TimeUnit.MINUTES
        );
    }

    /**
     * Create a new session and return its ID.
     */
    public String createSession() {
        String sessionId = generateSessionId();
        Session session = new Session(sessionId);
        sessions.put(sessionId, session);
        return sessionId;
    }

    /**
     * Get a session by ID, returns null if not found or expired.
     */
    public Session getSession(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        
        // Check if session has expired
        if (System.currentTimeMillis() - session.getLastAccessed() > sessionTimeoutMillis) {
            sessions.remove(sessionId);
            return null;
        }
        
        // Update last accessed time
        session.updateLastAccessed();
        return session;
    }

    /**
     * Invalidate a session by ID.
     */
    public void invalidateSession(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * Generate a secure random session ID.
     */
    private String generateSessionId() {
        byte[] bytes = new byte[32]; // 256 bits
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Clean up expired sessions.
     */
    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> 
            now - entry.getValue().getLastAccessed() > sessionTimeoutMillis
        );
    }

    /**
     * Shutdown the session manager and cleanup executor.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get the number of active sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}

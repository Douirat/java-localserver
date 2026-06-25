package http.connecting.state;

public enum ConnectionState {
        READING,
        PROCESSING,
        WRITING_HEADERS,
        WRITING_BODY,
        CLOSED
}
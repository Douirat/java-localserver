package http.connection;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

import config.ServerConfig;
import http.parser.HttpParser;
import http.request.Request;
import http.response.Response;

public class ConnectionState {

    // This specific client's socket.
    private final SocketChannel client;

    // Every ServerConfig block bound to the same host:port as this connection.
    // The actual one is picked once we've read the Host header.
    private final List<ServerConfig> candidates;

    // Bytes received from THIS client.
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);

    // Bytes of the serialized Response, pending write. Null until a request is complete.
    private ByteBuffer writeBuffer;

    // Stateful parser for this specific connection.
    private final HttpParser parser = new HttpParser();

    // The request currently being processed.
    private Request request;

    // The response currently being sent.
    private Response response;

    private long filePosition = 0;

    // Used for idle timeout tracking.
    private long lastActivity;

        public ConnectionState(SocketChannel client, List<ServerConfig> candidates) {
                this.client = client;
                this.candidates = candidates;
                this.parser.setCandidates(candidates);
                this.lastActivity = System.currentTimeMillis();
        }

        public SocketChannel getClient() {
                return client;
        }

        public List<ServerConfig> getCandidates() {
                return candidates;
        }

        public ByteBuffer getReadBuffer() {
                return readBuffer;
        }

        public ByteBuffer getWriteBuffer() {
                return writeBuffer;
        }

        public void setWriteBuffer(ByteBuffer writeBuffer) {
                this.writeBuffer = writeBuffer;
        }

        public HttpParser getParser() {
                return parser;
        }

        public Request getRequest() {
                return request;
        }

        public void setRequest(Request request) {
                this.request = request;
        }

        public Response getResponse() {
                return response;
        }

        public void setResponse(Response response) {
                this.response = response;
        }

        public long getFilePosition() {
                return filePosition;
        }

        public void setFilePosition(long p) {
                this.filePosition = p;
        }

        public long getLastActivity() {
                return lastActivity;
        }

        public void updateActivity() {
                lastActivity = System.currentTimeMillis();
        }
}
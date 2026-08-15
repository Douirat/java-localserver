package http.connection;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import http.parser.HttpParser;
import http.request.Request;
import http.response.Response;

public class ConnectionState {

        // This specific client's socket.
        private final SocketChannel client;

        // Bytes received from THIS client.
        ByteBuffer readBuffer = ByteBuffer.allocate(8192);

        // Stateful parser for this specific connection.
        private final HttpParser parser = new HttpParser();

        // HTTP parser belonging to THIS connection.
        // HttpParser parser =
        // new HttpParser();

        // The request currently being processed.
        Request request;

        // The response currently being sent.
        Response response;

        // Used later for idle timeout.

        private long lastActivity;

        public ConnectionState(SocketChannel client) {
                this.client = client;
                this.lastActivity = System.currentTimeMillis();
        }

        public SocketChannel getClient() {
                return client;
        }

        public ByteBuffer getReadBuffer() {
                return readBuffer;
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

        public long getLastActivity() {
                return lastActivity;
        }

        public void updateActivity() {
                lastActivity = System.currentTimeMillis();
        }
}
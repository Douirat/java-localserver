package http.connection;


import http.request.HttpRequest;
import http.response.Response;

public class ConnectionState {

    // Bytes received from THIS client.
    ByteBuffer readBuffer =
            ByteBuffer.allocate(8192);

    // HTTP parser belonging to THIS connection.
    HttpParser parser =
            new HttpParser();

    // The request currently being processed.
    Request request;

    // The response currently being sent.
    Response response;

    // Used later for idle timeout.
    long lastActivity =
            System.currentTimeMillis();
}
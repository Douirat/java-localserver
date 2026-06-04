package http.connecting;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import http.connecting.state.ConnectionState;
import http.connecting.state.RequestState;

public interface Connecting{
    SocketChannel getChannel();
    ByteBuffer getReadBuffer();
    ByteBuffer getWriteBuffer();
    ConnectionState getConnectionState();
    RequestState getRequestState();
    void setConnectionState(ConnectionState state);
    void ParseRequest();
    int parseHeaders(String[] lines);
    void parseBody(int bodyStart);
    void prepareResponse(String body); // for debugging purposes, we will prepare a simple response with a body only.
}
package http.connecting;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import http.connecting.state.ConnectionState;
import http.connecting.state.RequestState;
import http.request.*;
import http.response.*;

public interface Connecting{
    SocketChannel getChannel();
    ByteBuffer getReadBuffer();
    ByteBuffer getWriteBuffer();
    Response getResponse();
    Request getRequest();
    void setResponse(Response response);
    ConnectionState getConnectionState();
    RequestState getRequestState();
    void setConnectionState(ConnectionState state);
    void ParseRequest();
    int parseHeaders(String[] lines);
    void parseBody(int bodyStart);
    void prepareResponse();
}
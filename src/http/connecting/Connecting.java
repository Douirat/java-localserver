package http.connecting;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public interface Connecting{
    SocketChannel getChannel();
    ByteBuffer getReadBuffer();
    ByteBuffer getWriteBuffer();
    void ParseRequest();
    int parseHeaders(String[] lines);
    void parseBody(int bodyStart);
    void prepareResponse(String body); // for debugging purposes, we will prepare a simple response with a body only.
}
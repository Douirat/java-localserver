package http.connecting;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public interface Connecting{
    SocketChannel getChannel();
    ByteBuffer getReadBuffer();
    ByteBuffer getWriteBuffer();
    void prepareResponse(String body);
}
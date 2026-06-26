package http.connecting;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

import http.connecting.state.ConnectionState;
import http.connecting.state.RequestState;
import http.request.*;
import http.response.*;

public interface Connecting {
    SocketChannel getChannel();

    // ByteBuffer getHeadersBuffer();

    // ByteBuffer getBodyBuffer();

    // ByteBuffer getWriteBuffer();

    ByteBuffer getBuffer();

    Response getResponse();

    Request getRequest();

    boolean isFileResponse();

    FileChannel getFileChannel();

    long getFilePosition();

    long getFileSize();

    void setResponse(Response response);

    ConnectionState getConnectionState();

    RequestState getRequestState();

    void setConnectionState(ConnectionState state);

    void setAsStaticResponse();

    void setFileChannel(FileChannel fileChannel);

    void setFileSize(long size);

    void setFilePosition(int position);

    void ParseRequest();

    int parseHeaders(String[] lines);

    void parseBody(int bodyStart);

    void prepareResponse();
}
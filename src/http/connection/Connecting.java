package http.connection;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

import config.ServerConfig;
import http.parser.HttpParser;
import http.request.Request;
import http.response.Response;

public interface Connecting {

    SocketChannel getClient();

    List<ServerConfig> getCandidates();

    ByteBuffer getReadBuffer();

    ByteBuffer getWriteBuffer();

    void setWriteBuffer(ByteBuffer writeBuffer);

    HttpParser getParser();

    Request getRequest();

    void setRequest(Request request);

    Response getResponse();

    void setResponse(Response response);

    long getFilePosition();

    void setFilePosition(long position);

    long getLastActivity();

    void updateActivity();
}
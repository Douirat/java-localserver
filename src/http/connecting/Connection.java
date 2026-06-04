package http.connecting;

import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import http.response.Responding;
import java.nio.ByteBuffer;

import http.request.*;
import http.connecting.state.*;

public class Connection implements Connecting {
    private SocketChannel channel;
    private Requesting request = null;
    private Responding response = null;

    // buffers for reading and writing:
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    final ByteBuffer writeBuffer = ByteBuffer.allocate(8192);




    private ConnectionState state = ConnectionState.READING;
    private RequestState requestState = RequestState.REQUEST_LINE;

    public Connection(SocketChannel channel) {
        this.channel = channel;
    }

    // getters and setters:
    @Override
    public SocketChannel getChannel() {
        return this.channel;
    }


    @Override
    public ByteBuffer getReadBuffer() {
        return this.readBuffer;
    }


    @Override
    public ByteBuffer getWriteBuffer() {
        return this.writeBuffer;
    }

    @Override
    public ConnectionState getConnectionState() {
        return this.state;
    }

    @Override
    public RequestState getRequestState(){
        return this.requestState;
    }

    @Override
    public void setConnectionState(ConnectionState state) {
        this.state = state;
    }

    /*
     * Parses the HTTP request from the read buffer and prepares the response.
     */
    @Override
    public void ParseRequest() {

                if (state != ConnectionState.READING) {
                    return;
                }
                readBuffer.flip();

                    int limit = readBuffer.limit();
                    int headerEnd = -1;

                    // find \r\n\r\n manually in the buffer
                    for (int i = 0; i < limit - 3; i++) {
                        if (readBuffer.get(i) == '\r'
                                && readBuffer.get(i + 1) == '\n'
                                && readBuffer.get(i + 2) == '\r'
                                && readBuffer.get(i + 3) == '\n') {
                            headerEnd = i;
                            break;
                        }
                    }

            if (headerEnd == -1) {
                readBuffer.compact();
                return; // not enough data yet
            }


                byte[] headerBytes = new byte[headerEnd];
                for (int i = 0; i < headerEnd; i++) {
                    headerBytes[i] = readBuffer.get(i);
                }

                String headersText = new String(headerBytes, StandardCharsets.UTF_8);
                String[] lines = headersText.split("\r\n");


                if (lines.length < 1) {
                    throw new RuntimeException("Invalid HTTP request");
                }

                    // instantiate the request.
                    if (request == null) {
                        request = new Request();
                    }

                    
                // parse headers and determine if we have a body to read:
                int contentLength = parseHeaders(lines);   
                if(contentLength > 0){
                    this.parseBody(headerEnd + 4);
                } else {
                    this.requestState = RequestState.COMPLETE;
                }

        if (requestState == RequestState.COMPLETE) {
            System.out.println("------ ===> request after parsing body <=== ------\n " + this.request.toString());
            state = ConnectionState.PROCESSING;
            readBuffer.clear();
        }
    }

    @Override
    public int parseHeaders(String[] lines) {
        // Implementation for parsing headers
        if(requestState == RequestState.REQUEST_LINE){
            String[] requestLine = lines[0].split(" ");
            if(requestLine.length != 3) {
                throw new RuntimeException("Invalid HTTP request line");
            }
            this.request.setRequestLine(requestLine);
            this.requestState = RequestState.HEADERS;
        }

        // parse headers:
            if(requestState == RequestState.HEADERS){
                    for(int i=1; i<lines.length; i++){
                        if(lines[i].isEmpty()){
                            break; // end of headers
                        }
                    String[] header = lines[i].split(": ", 2);
                    if(header.length != 2){
                        throw new RuntimeException("Invalid HTTP header: ");
                    }
                    this.request.addHeader(header[0], header[1]);
                }
            }


                // extract the length of the body if exists:
                String contentLength = this.request.getHeaders().get("Content-Length");
                if(contentLength != null){
                    this.requestState = RequestState.BODY;
                    return Integer.parseInt(contentLength);
                }
                return 0;
    }

    @Override
    public void parseBody(int bodyStart) {
        int contentLength = Integer.parseInt(this.request.getHeaders().getOrDefault("Content-Length", "0"));

        int available = readBuffer.limit() - bodyStart;

        if (available < contentLength) {
            readBuffer.compact();
            return; // wait for more data
        }

        byte[] bodyBytes = new byte[contentLength];

        readBuffer.position(bodyStart);
        readBuffer.get(bodyBytes);
        this.request.setBody(bodyBytes);
        this.requestState = RequestState.COMPLETE;
    }

    @Override
    public void prepareResponse(String body) {
        writeBuffer.clear();

        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: " + body.length() + "\r\n" +
                "\r\n" +
                body;

        writeBuffer.put(response.getBytes(StandardCharsets.UTF_8));
        writeBuffer.flip();
    }

}
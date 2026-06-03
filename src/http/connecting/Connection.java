package http.connecting;

import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import http.request.Requesting;
import http.response.Responding;
import java.nio.ByteBuffer;

import http.request.*;

public class Connection implements Connecting {
    private SocketChannel channel;
    private Requesting request = null;
    private Responding response = null;

    // buffers for reading and writing:
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
    final ByteBuffer writeBuffer = ByteBuffer.allocate(8192);

    // state
    enum State {
        READING,
        PROCESSING,
        WRITING,
        CLOSED
    }

    enum RequestState {
        REQUEST_LINE,
        HEADERS,
        BODY,
        COMPLETE
    }

    private State state = State.READING;
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


    /*
     * Parses the HTTP request from the read buffer and prepares the response.
     */
    @Override
    public void ParseRequest() {
        if (state != State.READING) {
            return;
        }
        readBuffer.flip();
     
        // Reading the request line and headers:
        String requestData = StandardCharsets.UTF_8.decode(readBuffer).toString();
        String[] lines = requestData.split("\r\n");
        if (lines.length < 1) {
            throw new RuntimeException("Invalid HTTP request");
        }
        if (request == null) {
            request = new Request();
        }
        int contentLength = parseHeaders(lines);
        System.out.println("Content-Length: " + this.request.toString());
        if(contentLength > 0){
            state = State.PROCESSING;

        }else{
            state = State.WRITING;
        }
        readBuffer.clear();
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

        if(requestState == RequestState.HEADERS){
            for(int i=1; i<lines.length; i++){
                if(lines[i].isEmpty()){
                    continue; // TODO: treat this case properly.
                }
                String[] header = lines[i].split(": ", 2);
            if(header.length != 2){
                continue; // TODO: treat this case properly.
            }
            this.request.addHeader(header[0], header[1]);
        }
        this.requestState = RequestState.BODY;
    }
        // extract the length of the body if exists:
        String contentLength = this.request.getHeaders().get("Content-Length");
        if(contentLength != null){
            return Integer.parseInt(contentLength);
        }
        return 0;
    }

    @Override
    public void parseBody() {
        // Implementation for parsing body
        if(this.requestState == RequestState.BODY){
           byte[] bodyBytes = new byte[readBuffer.remaining()];
            readBuffer.get(bodyBytes);
            this.request.setBody(bodyBytes);
            this.requestState = RequestState.COMPLETE;  
        }
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
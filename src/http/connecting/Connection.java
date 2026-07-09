package http.connecting;

import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import http.response.Responding;
import http.response.Response;
import http.response.cookie.Cookie;

import java.nio.ByteBuffer;

import http.request.*;
import http.connecting.state.*;
import http.json.Serializer;

public class Connection implements Connecting {

    private SocketChannel channel;
    private Requesting request = null;
    private Responding response = null;

    // buffers for reading and writing:
    // private final ByteBuffer headersBuffer = ByteBuffer.allocate(65536);
    // private final ByteBuffer bodyBuffer = ByteBuffer.allocate(65536);
    // private final ByteBuffer writeBuffer = ByteBuffer.allocate(65536);

    private final ByteBuffer buffer = ByteBuffer.allocate(65536); // 64KB for better handling of large bodies

    private boolean bufferFlipped = false;
    private boolean isStatic = false;
    private int maxBodySize = 10485760; // 10MB default

    // Connection timeout tracking
    private long lastActivityTime;
    private final long connectionTimeoutMillis = 300000; // 5 minutes default

    // Keep track of the connection state.
    private ConnectionState state = ConnectionState.READING;

    // reques state management.
    private RequestState requestState = RequestState.REQUEST_LINE;

    /**
     * In case of a static file so i have the possibility to handle static files as
     * well:
     */
    private FileChannel fileChannel;
    private long filePosition;
    private long fileSize;

    /**
     * FileChannel fc =
     * FileChannel.open(path);
     * connection.setFileChannel(fc);
     * connection.setFilePosition(0);
     * connection.setFileSize(fc.size());
     */

    public Connection(SocketChannel channel) {
        this.channel = channel;
        this.lastActivityTime = System.currentTimeMillis();
    }

    // getters and setters:
    @Override
    public SocketChannel getChannel() {
        return this.channel;
    }

    @Override
    public Request getRequest() {
        return (Request) this.request;
    }

    @Override
    public Response getResponse() {
        return (Response) this.response;
    }

    // @Override
    // public ByteBuffer getHeadersBuffer() {
    // return this.headersBuffer;
    // }

    // @Override
    // public ByteBuffer getBodyBuffer() {
    // return this.bodyBuffer;
    // }

    // @Override
    // public ByteBuffer getWriteBuffer() {
    // return this.writeBuffer;
    // }

    @Override
    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    @Override
    public ConnectionState getConnectionState() {
        return this.state;
    }

    @Override
    public RequestState getRequestState() {
        return this.requestState;
    }

    @Override
    public boolean isStaticResponse() {
        return isStatic;
    }

    @Override
    public FileChannel getFileChannel() {
        return this.fileChannel;
    }

    @Override
    public long getFilePosition() {
        return this.filePosition;
    }

    @Override
    public long getFileSize() {
        return this.fileSize;
    }

    @Override
    public void setConnectionState(ConnectionState state) {
        this.state = state;
    }

    @Override
    public void setResponse(Response response) {
        this.response = response;
    }

    @Override
    public void setAsStaticResponse() {
        this.setConnectionState(ConnectionState.WRITING_HEADERS);
        this.isStatic = true;
    }

    @Override
    public void setFileChannel(FileChannel fileChannel) {
        this.fileChannel = fileChannel;
    }

    @Override
    public void setFileSize(long size) {
        this.fileSize = size;
    }

    @Override
    public void setFilePosition(long position) {
        this.filePosition = position;
    }

    public void setMaxBodySize(int maxSize) {
        this.maxBodySize = maxSize;
    }

    public int getMaxBodySize() {
        return this.maxBodySize;
    }

    public void updateLastActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public boolean isTimedOut() {
        return System.currentTimeMillis() - lastActivityTime > connectionTimeoutMillis;
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public long getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    public void loadBuffer(byte[] bytes) {
        buffer.clear();

        if (bytes.length > buffer.remaining()) {
            throw new IllegalStateException("Not enough space in buffer");
        }

        buffer.put(bytes);
        buffer.flip();
    }

    /*
     * Parses the HTTP request from the read buffer and prepares the response.
     */
    @Override
    public void ParseRequest() {

        if (state != ConnectionState.READING) {
            return;
        }

        // ← only flip once, not on every call
        if (!bufferFlipped) { // see note below
            buffer.flip();
            bufferFlipped = true;
        }

        int limit = buffer.limit();
        int headerEnd = -1;

        for (int i = 0; i < limit - 3; i++) {
            if (buffer.get(i) == '\r'
                    && buffer.get(i + 1) == '\n'
                    && buffer.get(i + 2) == '\r'
                    && buffer.get(i + 3) == '\n') {
                headerEnd = i;
                break;
            }
        }

        if (headerEnd == -1) {
            buffer.compact(); // ← compact switches back to write mode safely
            bufferFlipped = false;
            return;
        }

        byte[] headerBytes = new byte[headerEnd];
        for (int i = 0; i < headerEnd; i++) {
            headerBytes[i] = buffer.get(i);
        }

        String headersText = new String(headerBytes, StandardCharsets.UTF_8);
        String[] lines = headersText.split("\r\n");

        if (lines.length < 1) {
            throw new RuntimeException("Invalid HTTP request");
        }

        if (request == null) {
            request = new Request();
        }

        int contentLength = parseHeaders(lines);
        if (contentLength > 0) {
            this.parseBody(headerEnd + 4);
        } else {
            this.requestState = RequestState.COMPLETE;
        }

        if (requestState == RequestState.COMPLETE) {
            state = ConnectionState.PROCESSING;
            buffer.clear();
            bufferFlipped = false;
        }
    }

    @Override
    public int parseHeaders(String[] lines) {
        // Implementation for parsing headers
        if (requestState == RequestState.REQUEST_LINE) {
            // Reset isStatic flag for new request
            this.isStatic = false;

            String[] requestLine = lines[0].split(" ");
            if (requestLine.length != 3) {
                throw new RuntimeException("Invalid HTTP request line");
            }
            this.request.setRequestLine(requestLine);
            this.requestState = RequestState.HEADERS;
        }

        // parse headers:
        if (requestState == RequestState.HEADERS) {
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].isEmpty()) {
                    break; // end of headers
                }
                String[] header = lines[i].split(": ", 2);
                if (header.length != 2) {
                    throw new RuntimeException("Invalid HTTP header: ");
                }
                this.request.addHeader(header[0], header[1]);

                // Parse Cookie header if present
                if (header[0].equalsIgnoreCase("Cookie")) {
                    String[] cookies = header[1].split(";");
                    for (String cookie : cookies) {
                        String[] keyValue = cookie.trim().split("=", 2);
                        if (keyValue.length == 2) {
                            this.request.addCookie(keyValue[0].trim(), keyValue[1].trim());
                        }
                    }
                }
            }
        }

        // extract the length of the body if exists:
        String contentLength = this.request.getHeaders().get("Content-Length");
        if (contentLength != null) {
            int length = Integer.parseInt(contentLength);
            if (length > this.maxBodySize) {
                throw new RuntimeException("413 Payload Too Large: Content-Length " + length + " exceeds maximum " + this.maxBodySize);
            }
            this.requestState = RequestState.BODY;
            return length;
        }
        return 0;
    }

    @Override
    public void parseBody(int bodyStart) {
        int contentLength = Integer.parseInt(this.request.getHeaders().getOrDefault("Content-Length", "0"));

        int available = buffer.limit() - bodyStart;

        if (available < contentLength) {
            buffer.compact();
            return; // wait for more data
        }

        byte[] bodyBytes = new byte[contentLength];

        buffer.position(bodyStart);
        buffer.get(bodyBytes);
        this.request.setBody(bodyBytes);
        this.requestState = RequestState.COMPLETE;
    }

    @Override
    public void prepareResponse() {
        this.response.setVersion(this.request.getVersion());
        try {
            // 1. Serialize body to JSON
            String bodyJson = Serializer.toJson(this.response.getBody());

            // 2. Auto-set Content-Length based on serialized body
            byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);

            String headers = this.prepareHeaders(bodyBytes.length);

            StringBuilder sb = new StringBuilder(headers);

            // Body
            sb.append(bodyJson);

            // System.out.println("\n -----> Response \n" + sb.toString());

            // 4. Write everything to the buffer
            byte[] fullResponse = sb.toString().getBytes(StandardCharsets.UTF_8);
            this.buffer.clear(); // ← reset before each response
            this.buffer.put(fullResponse);
            this.buffer.flip(); // ← switch to read mode so channel.write() works

        } catch (Exception e) {
            throw new RuntimeException("Error serializing response", e);
        }
    }

    public String prepareHeaders(int size) {
        // 3. Build the response string
        StringBuilder sb = new StringBuilder();

        // Status line: HTTP/1.1 200 OK
        String version = this.response.getVersion() != null ? this.response.getVersion() : "HTTP/1.1";
        sb.append(version)
                .append(" ")
                .append(this.response.getStatus())
                .append(" ")
                .append(this.response.getStatusReason())
                .append("\r\n");

        // Headers
        for (Map.Entry<String, String> entry : this.response.getHeaders().entrySet()) {
            sb.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\r\n");
        }

        // Auto-inject Content-Length
        sb.append("Content-Length: ")
                .append(size)
                .append("\r\n");

        // Cookies (Set-Cookie headers)
        for (Cookie cookie : this.response.getCookies()) {
            sb.append("Set-Cookie: ")
                    .append(cookie.getName())
                    .append("=")
                    .append(cookie.getValue());

            if (cookie.getDomain() != null)
                sb.append("; Domain=").append(cookie.getDomain());
            if (cookie.getPath() != null)
                sb.append("; Path=").append(cookie.getPath());
            if (cookie.getExpires() != null)
                sb.append("; Expires=").append(cookie.getExpires());
            if (cookie.isHttpOnly())
                sb.append("; HttpOnly");
            if (cookie.isSecure())
                sb.append("; Secure");

            sb.append("\r\n");
        }

        // Blank line separating headers from body (mandated by HTTP spec)
        sb.append("\r\n");
        return sb.toString();
    }

}
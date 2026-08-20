package http.response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class Response {

    private int statusCode = 200;
    private String statusText = "OK";
    private final Map<String, String> headers = new LinkedHashMap<>();

    // short generated bodies (errors, upload success, directory listing)
    private byte[] body = new byte[0];

    // file streaming (zero-copy via transferTo)
    private FileChannel fileChannel;
    private long contentLength;

    public Response() {
    }

    public Response(int statusCode, String statusText) {
        this.statusCode = statusCode;
        this.statusText = statusText;
    }

    public void setStatus(int statusCode, String statusText) {
        this.statusCode = statusCode;
        this.statusText = statusText;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public void setBody(byte[] body) {
        this.body = (body != null) ? body : new byte[0];
        addHeader("Content-Length", String.valueOf(this.body.length));
    }

    public void setBody(String text) {
        setBody(text.getBytes(StandardCharsets.UTF_8));
    }

    public void setFileChannel(FileChannel fc, long size) {
        this.fileChannel = fc;
        this.contentLength = size;
        addHeader("Content-Length", String.valueOf(size));
    }

    public boolean isStreaming() {
        return fileChannel != null;
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public long getContentLength() {
        return contentLength;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public byte[] getBody() {
        return body;
    }

    /**
     * Serializes status line + headers + body into raw HTTP/1.1 bytes ready to
     * write.
     */
    // headers only — used before transferTo() in Server.write()
    public byte[] headersToBytes() {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(statusCode).append(' ').append(statusText).append("\r\n");
        for (Map.Entry<String, String> h : headers.entrySet()) {
            sb.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    // headers + body — only for short generated responses
    public byte[] toBytes() {
        byte[] head = headersToBytes();
        byte[] full = new byte[head.length + body.length];
        System.arraycopy(head, 0, full, 0, head.length);
        System.arraycopy(body, 0, full, head.length, body.length);
        return full;
    }

    public void closeFileChannel() {
        if (fileChannel != null) {
            try {
                fileChannel.close();
            } catch (IOException ignored) {
            }
            fileChannel = null;
        }
    }

    @Override
    public String toString() {
        return "Response{" +
                "statusCode=" + statusCode +
                ", statusText='" + statusText + '\'' +
                ", headers=" + headers +
                ", bodyLength=" + (isStreaming() ? contentLength : body.length) +
                (isStreaming() ? ", streaming=true" : ", body='" + new String(body, StandardCharsets.UTF_8) + "'") +
                '}';
    }

    public void debug() {
        System.out.println("debug the response ----> " + this.toString());
    }
}
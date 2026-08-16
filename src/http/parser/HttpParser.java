package http.parser;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;

import exceptions.BadRequestException;
import http.request.Request;

public class HttpParser {

    // The parser needs to remember where it is
    // between multiple SocketChannel.read() calls.
    private ParseState state = ParseState.REQUEST_LINE;

    private final StringBuilder currentLine = new StringBuilder();

    private String method;
    private String path;
    private String version;

    private final Map<String, String> headers = new HashMap<>();

    private final ByteArrayOutputStream body = new ByteArrayOutputStream();

    private int expectedBodyLength = 0;
    private int receivedBodyLength = 0;

    // Parse states of an HTTP request.
    private enum ParseState {
        REQUEST_LINE,
        HEADERS,
        BODY,
        COMPLETE
    }

    /**
     * Called every time SocketChannel.read() gives us
     * new bytes.
     *
     * Returns:
     *
     * HttpRequest -> request is complete
     * null -> need more bytes
     */
    public Request parse(ByteBuffer buffer) {

        // We will consume bytes from the buffer incrementally.
        while (buffer.hasRemaining()) {

            byte currentByte = buffer.get();

            switch (state) {
                case REQUEST_LINE -> parseRequestLineByte(currentByte);
                case HEADERS -> parseHeaderByte(currentByte);
                case BODY -> parseBodyByte(currentByte);
                case COMPLETE -> {
                    /* caller should reset() before parsing a new request */ }
            }

            if (state == ParseState.COMPLETE) {
                break;
            }
        }

        // IMPORTANT:
        // The request might NOT be complete yet.
        //
        // Example:
        //
        // First read:
        // GET /index
        //
        // parse() returns null because we haven't
        // received the complete request yet.
        //
        // Later another read happens and parse()
        // continues from the previous state.

        if (state == ParseState.COMPLETE) {
            return buildRequest();
        }

        return null;
    }

    private Request buildRequest() {
        Request request = new Request();
        request.setRequestLine(new String[] { method, path, version });
        for (Map.Entry<String, String> h : headers.entrySet()) {
            request.addHeader(h.getKey(), h.getValue());
        }
        if (body.size() > 0) {
            request.setBody(body.toByteArray());
        }
        return request;
    }

    /** Call before parsing the next request on a keep-alive connection. */
    public void reset() {
        state = ParseState.REQUEST_LINE;
        currentLine.setLength(0);
        method = null;
        path = null;
        version = null;
        headers.clear();
        body.reset();
        expectedBodyLength = 0;
        receivedBodyLength = 0;
    }

    private void parseRequestLineByte(byte b) {
        /**
         * POST /users HTTP/1.1\r\n
         * Host: localhost:8080\r\n
         * Content-Type: application/json\r\n
         * Content-Length: 31\r\n
         * \r\n
         * {"username":"bennacer"}
         */

        /**
         * HTTP/1.1 200 OK\r\n
         * Content-Type: image/png\r\n
         * Content-Length: 15234\r\n
         * \r\n
         * [BINARY PNG BYTES]
         */

        currentLine.append((char) b);
        // HTTP lines end with CRLF.
        if (currentLine.toString().endsWith("\r\n")) {
            String line = currentLine.substring(0, currentLine.length() - 2);

            String[] parts = line.split(" ");

            if (parts.length != 3) {
                throw new BadRequestException(
                        "Invalid request line");
            }

            method = parts[0];
            path = parts[1];
            version = parts[2];

            currentLine.setLength(0);

            state = ParseState.HEADERS;
        }
    }

    private void parseHeaderByte(byte b) {

        /**
         * // Accumulate:
         * // Host: localhost\r\n
         * // Content-Length: 5\r\n
         * //
         * // An empty CRLF means:
         * //
         * // headers are finished
         * //
         * // Then decide whether there is a body.
         */

        currentLine.append((char) b);

        if (currentLine.toString().endsWith("\r\n")) {
            String line = currentLine.substring(0, currentLine.length() - 2);

            currentLine.setLength(0);

            /**
             * Empty line means:
             * \r\n
             * Therefore the headers are finished.
             */
            if (line.isEmpty()) {
                String contentLength = headers.get("content-length");
                if (contentLength != null) {
                    expectedBodyLength = Integer.parseInt(contentLength);
                    // BUG FIX: this branch used to be empty, parser never left HEADERS
                    state = (expectedBodyLength > 0) ? ParseState.BODY : ParseState.COMPLETE;
                } else {
                    state = ParseState.COMPLETE;
                }
                return;
            }

            // Normal header:
            //
            // Host: localhost
            //
            // Content-Length: 5
            int colon = line.indexOf(":");
            if (colon <= 0) {
                throw new BadRequestException("Invalid header");
            }
            String name = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            headers.put(name, value);
        }

    }

    private void parseBodyByte(byte b) {

        // TODO: for now we will recieve the body as a normal body of bytes but later i
        // will have to check they image/video type and so on.
        // For Content-Length:
        //
        // read exactly Content-Length bytes.
        //
        // For chunked encoding:
        //
        // parse the chunk sizes and chunk data.
        body.write(b);
        receivedBodyLength++;
        if (receivedBodyLength == expectedBodyLength) {
            state = ParseState.COMPLETE;
        }
    }
}
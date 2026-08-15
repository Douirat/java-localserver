package http.parser;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.*;

public class HttpParser {

    // The parser needs to remember where it is
    // between multiple SocketChannel.read() calls.
    private ParseState state = ParseState.REQUEST_LINE;

    private final StringBuilder requestLine = new StringBuilder();

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
    public HttpRequest parse(ByteBuffer buffer) {

        // We will consume bytes from the buffer incrementally.
        while (buffer.hasRemaining()) {

            byte currentByte = buffer.get();

            switch (state) {

                case REQUEST_LINE:
                    parseRequestLineByte(currentByte);
                    break;

                case HEADERS:
                    parseHeaderByte(currentByte);
                    break;

                case BODY:
                    parseBodyByte(currentByte);
                    break;

                case COMPLETE:
                    // The request is already complete.
                    // The caller should create/reset the parser
                    // for the next request if keep-alive is used.
                    break;
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

        return null;
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

        requestLine.append((char) b);
        // HTTP lines end with CRLF.
        if(requestLine.toString().endsWith("\r\n")){
            String line = requestLine.substring(0, requestLine.length()-2);

            String[] parts = line.split(" ");

            if (parts.length != 3) {
                // TODO: make a costum BadRequestException.
                throw new RuntimeException(
                        "Invalid request line");
            }

            method = parts[0];
            path = parts[1];
            version = parts[2];

            state = ParseState.HEADERS;
        }
    }

    private void parseHeaderByte(byte b) {

        // TODO:
        // Accumulate:
        //
        // Host: localhost\r\n
        // Content-Length: 5\r\n
        //
        // An empty CRLF means:
        //
        // headers are finished
        //
        // Then decide whether there is a body.
    }

    private void parseBodyByte(byte b) {

        // TODO:
        // For Content-Length:
        //
        // read exactly Content-Length bytes.
        //
        // For chunked encoding:
        //
        // parse the chunk sizes and chunk data.
    }
}
package http.parser;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.*;

public class HttpParser {

    // The parser needs to remember where it is
    // between multiple SocketChannel.read() calls.
    private ParseState state = ParseState.REQUEST_LINE;

    private final StringBuilder requestLine =
            new StringBuilder();

    private final Map<String, String> headers =
            new HashMap<>();

    private final ByteArrayOutputStream body =
            new ByteArrayOutputStream();


    // Parse states of an HTTP request.
    private enum ParseState {
        REQUEST_LINE,
        HEADERS,
        BODY,
        COMPLETE
    }


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

        // TODO:
        // Accumulate bytes until CRLF:
        //
        // GET /index.html HTTP/1.1\r\n
        //
        // Then extract:
        //
        // method  = GET
        // path    = /index.html
        // version = HTTP/1.1
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
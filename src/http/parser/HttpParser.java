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

    // --- Chunked mode ---
    // Sub-states when reading a chunked body.
    //   CHUNK_SIZE  – reading the hex size line (ends with \r\n)
    //   CHUNK_DATA  – reading exactly chunkRemaining bytes of payload
    //   CHUNK_TRAIL – reading the trailing \r\n after chunk data
    private ChunkState chunkState = ChunkState.CHUNK_SIZE;

    // How many payload bytes remain in the current chunk.
    // -1 means we just finished the last "0" chunk and are draining its trailer.
    private int chunkRemaining = 0;

    // Accumulates hex-size lines and chunk trailers.
    private final StringBuilder chunkLine = new StringBuilder();

    // Parse states of an HTTP request.
    private enum ParseState {
        REQUEST_LINE,
        HEADERS,
        BODY,
        CHUNKED_BODY,
        COMPLETE
    }

    private enum ChunkState {
        CHUNK_SIZE,
        CHUNK_DATA,
        CHUNK_TRAIL
    }

    /**
     * Called every time SocketChannel.read() gives us new bytes.
     *
     * Returns:
     *
     * Throws BadRequestException for malformed requests; the caller
     * (Server.read) must catch this and return a 400 response instead of crashing.
     */
    public Request parse(ByteBuffer buffer) {

        while (buffer.hasRemaining()) {

            byte currentByte = buffer.get();

            switch (state) {
                case REQUEST_LINE -> parseRequestLineByte(currentByte);
                case HEADERS      -> parseHeaderByte(currentByte);
                case BODY         -> parseBodyByte(currentByte);
                case CHUNKED_BODY -> parseChunkedByte(currentByte);
                case COMPLETE     -> { /* caller should reset() before parsing a new request */ }
            }

            if (state == ParseState.COMPLETE) {
                break;
            }
        }

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
        // Parse Cookie header into individual cookie key-value pairs.
        String cookieHeader = headers.get("cookie");
        if (cookieHeader != null) {
            parseCookies(request, cookieHeader);
        }
        if (body.size() > 0) {
            request.setBody(body.toByteArray());
        }
        return request;
    }

    /**
     * Parses a "Cookie: name=value; name2=value2" header value and stores
     * each pair into the request's cookie map so request.getCookie(name) works.
     */
    private void parseCookies(Request request, String cookieHeader) {
        for (String part : cookieHeader.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name  = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (!name.isEmpty()) {
                request.addCookie(name, value);
            }
        }
    }

    /** Call before parsing the next request on a keep-alive connection. */
    public void reset() {
        state = ParseState.REQUEST_LINE;
        currentLine.setLength(0);
        method  = null;
        path    = null;
        version = null;
        headers.clear();
        body.reset();
        expectedBodyLength = 0;
        receivedBodyLength = 0;
        chunkState     = ChunkState.CHUNK_SIZE;
        chunkRemaining = 0;
        chunkLine.setLength(0);
    }

    // Request-line parsing
    private void parseRequestLineByte(byte b) {
        currentLine.append((char) b);
        if (currentLine.toString().endsWith("\r\n")) {
            String line = currentLine.substring(0, currentLine.length() - 2);

            String[] parts = line.split(" ");
            if (parts.length != 3) {
                throw new BadRequestException("Invalid request line: '" + line + "'");
            }

            method  = parts[0];
            path    = parts[1];
            version = parts[2];

            currentLine.setLength(0);
            state = ParseState.HEADERS;
        }
    }

    // Header parsing
    private void parseHeaderByte(byte b) {
        currentLine.append((char) b);

        if (!currentLine.toString().endsWith("\r\n")) {
            return;
        }

        String line = currentLine.substring(0, currentLine.length() - 2);
        currentLine.setLength(0);

        // Empty line means end-of-headers.
        if (line.isEmpty()) {
            decideBodyMode();
            return;
        }

        int colon = line.indexOf(':');
        if (colon <= 0) {
            throw new BadRequestException("Invalid header line: '" + line + "'");
        }
        String name  = line.substring(0, colon).trim().toLowerCase();
        String value = line.substring(colon + 1).trim();
        headers.put(name, value);
    }

    private void decideBodyMode() {
        String te = headers.get("transfer-encoding");
        if (te != null && te.toLowerCase().contains("chunked")) {
            chunkState = ChunkState.CHUNK_SIZE;
            chunkLine.setLength(0);
            state = ParseState.CHUNKED_BODY;
            return;
        }

        String cl = headers.get("content-length");
        if (cl != null) {
            try {
                expectedBodyLength = Integer.parseInt(cl.trim());
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid Content-Length value: '" + cl + "'");
            }
            state = (expectedBodyLength > 0) ? ParseState.BODY : ParseState.COMPLETE;
            return;
        }

        // No body indicator.
        state = ParseState.COMPLETE;
    }

    private void parseBodyByte(byte b) {
        body.write(b);
        receivedBodyLength++;
        if (receivedBodyLength == expectedBodyLength) {
            state = ParseState.COMPLETE;
        }
    }

    private void parseChunkedByte(byte b) {
        switch (chunkState) {

            case CHUNK_SIZE -> {
                // Accumulate bytes until we see \r\n.
                chunkLine.append((char) b);
                if (!chunkLine.toString().endsWith("\r\n")) {
                    return;
                }

                String sizeLine = chunkLine.substring(0, chunkLine.length() - 2);
                chunkLine.setLength(0);

                // Strip optional chunk extensions: "1a3f;name=value" -> "1a3f"
                int semi = sizeLine.indexOf(';');
                if (semi >= 0) {
                    sizeLine = sizeLine.substring(0, semi);
                }
                sizeLine = sizeLine.trim();

                try {
                    chunkRemaining = Integer.parseInt(sizeLine, 16);
                } catch (NumberFormatException e) {
                    throw new BadRequestException("Invalid chunk size: '" + sizeLine + "'");
                }

                if (chunkRemaining == 0) {
                    // Last-chunk — we still need to consume its trailing \r\n.
                    // Use chunkRemaining == -1 as a sentinel meaning "drain-last-trailer".
                    chunkRemaining = -1;
                    chunkState = ChunkState.CHUNK_TRAIL;
                } else {
                    chunkState = ChunkState.CHUNK_DATA;
                }
            }

            case CHUNK_DATA -> {
                body.write(b);
                chunkRemaining--;
                if (chunkRemaining == 0) {
                    // Move on to the trailing \r\n of this chunk.
                    chunkState = ChunkState.CHUNK_TRAIL;
                }
            }

            case CHUNK_TRAIL -> {
                // Consume "\r\n" that terminates a normal chunk,
                // or the blank-line trailer that follows the last "0" chunk.
                chunkLine.append((char) b);
                if (!chunkLine.toString().endsWith("\r\n")) {
                    return;
                }
                chunkLine.setLength(0);

                if (chunkRemaining == -1) {
                    // We just consumed the trailer after the last chunk -> done.
                    state = ParseState.COMPLETE;
                } else {
                    // Normal chunk finished; wait for the next chunk-size line.
                    chunkState = ChunkState.CHUNK_SIZE;
                }
            }
        }
    }
}
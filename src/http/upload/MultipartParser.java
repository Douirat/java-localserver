package http.upload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses multipart/form-data requests for file uploads.
 */
public class MultipartParser {

    private final byte[] boundary;
    private final byte[] data;

    public MultipartParser(String boundary, byte[] data) {
        this.boundary = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
        this.data = data;
    }

    /**
     * Parse the multipart data and return a list of parts.
     */
    public List<MultipartPart> parse() throws IOException {
        List<MultipartPart> parts = new ArrayList<>();
        int pos = 0;

        // Skip initial boundary
        pos = findBoundary(pos);
        if (pos == -1) {
            return parts;
        }
        pos += boundary.length;

        // Skip CRLF after boundary
        if (pos < data.length && data[pos] == '\r') pos++;
        if (pos < data.length && data[pos] == '\n') pos++;

        while (pos < data.length) {
            // Check for final boundary (boundary + "--")
            if (pos + boundary.length + 2 <= data.length) {
                boolean isFinal = true;
                for (int i = 0; i < boundary.length; i++) {
                    if (data[pos + i] != boundary[i]) {
                        isFinal = false;
                        break;
                    }
                }
                if (isFinal && data[pos + boundary.length] == '-' && data[pos + boundary.length + 1] == '-') {
                    break;
                }
            }

            // Parse headers
            Map<String, String> headers = parseHeaders(pos);
            pos = findNextLine(pos);
            if (pos == -1) break;
            pos++; // Skip CRLF

            // Find end of this part
            int partEnd = findBoundary(pos);
            if (partEnd == -1) {
                // No more boundaries, rest is the last part
                partEnd = data.length;
            }

            // Extract part data
            int partLength = partEnd - pos;
            // Subtract CRLF before boundary
            if (partLength >= 2 && data[partEnd - 2] == '\r' && data[partEnd - 1] == '\n') {
                partLength -= 2;
            }

            byte[] partData = new byte[partLength];
            System.arraycopy(data, pos, partData, 0, partLength);

            MultipartPart part = new MultipartPart(headers, partData);
            parts.add(part);

            pos = partEnd + boundary.length;
            // Skip CRLF after boundary
            if (pos < data.length && data[pos] == '\r') pos++;
            if (pos < data.length && data[pos] == '\n') pos++;
        }

        return parts;
    }

    /**
     * Find the next boundary starting from position.
     */
    private int findBoundary(int startPos) {
        for (int i = startPos; i <= data.length - boundary.length; i++) {
            boolean match = true;
            for (int j = 0; j < boundary.length; j++) {
                if (data[i + j] != boundary[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Parse headers starting from position.
     */
    private Map<String, String> parseHeaders(int startPos) {
        Map<String, String> headers = new HashMap<>();
        int pos = startPos;
        int end = findEmptyLine(startPos);
        if (end == -1) {
            return headers;
        }

        String headerText = new String(data, pos, end - pos, StandardCharsets.UTF_8);
        String[] lines = headerText.split("\r\n");

        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                headers.put(key, value);
            }
        }

        return headers;
    }

    /**
     * Find an empty line (CRLF CRLF) starting from position.
     */
    private int findEmptyLine(int startPos) {
        for (int i = startPos; i < data.length - 3; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find the next CRLF starting from position.
     */
    private int findNextLine(int startPos) {
        for (int i = startPos; i < data.length - 1; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }
}

package http.transfer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Decodes HTTP chunked transfer encoding.
 * Format: <chunk-size in hex>\r\n<chunk-data>\r\n ... 0\r\n\r\n
 */
public class ChunkedDecoder {

    private byte[] data;
    private int position;

    public ChunkedDecoder(byte[] data) {
        this.data = data;
        this.position = 0;
    }

    /**
     * Decode chunked data and return the complete body.
     */
    public byte[] decode() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        while (position < data.length) {
            // Read chunk size line
            String chunkSizeLine = readLine();
            if (chunkSizeLine == null || chunkSizeLine.isEmpty()) {
                break;
            }

            // Parse chunk size (hexadecimal)
            int chunkSize;
            try {
                // Chunk size may have extensions after semicolon
                String sizeStr = chunkSizeLine.split(";")[0].trim();
                chunkSize = Integer.parseInt(sizeStr, 16);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid chunk size: " + chunkSizeLine);
            }

            // Chunk size 0 indicates end of chunks
            if (chunkSize == 0) {
                // Skip trailing headers if any
                while (position < data.length) {
                    String line = readLine();
                    if (line == null || line.isEmpty()) {
                        break;
                    }
                }
                break;
            }

            // Read chunk data
            if (position + chunkSize > data.length) {
                throw new IOException("Unexpected end of data while reading chunk");
            }

            output.write(data, position, chunkSize);
            position += chunkSize;

            // Read CRLF after chunk data
            if (position < data.length && data[position] == '\r') {
                position++;
            }
            if (position < data.length && data[position] == '\n') {
                position++;
            }
        }

        return output.toByteArray();
    }

    /**
     * Read a line ending with CRLF from the data.
     */
    private String readLine() {
        if (position >= data.length) {
            return null;
        }

        int start = position;
        int end = position;

        while (end < data.length) {
            if (data[end] == '\r' && end + 1 < data.length && data[end + 1] == '\n') {
                String line = new String(data, start, end - start, StandardCharsets.UTF_8);
                position = end + 2;
                return line;
            }
            end++;
        }

        // No CRLF found, return remaining data
        if (start < data.length) {
            String line = new String(data, start, data.length - start, StandardCharsets.UTF_8);
            position = data.length;
            return line;
        }

        return null;
    }

    /**
     * Check if data appears to be chunked encoded.
     */
    public static boolean isChunked(String transferEncoding) {
        return transferEncoding != null && transferEncoding.equalsIgnoreCase("chunked");
    }
}

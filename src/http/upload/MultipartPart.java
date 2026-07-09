package http.upload;

import java.util.Map;

/**
 * Represents a single part in a multipart/form-data request.
 */
public class MultipartPart {

    private final Map<String, String> headers;
    private final byte[] data;
    private String name;
    private String filename;
    private String contentType;

    public MultipartPart(Map<String, String> headers, byte[] data) {
        this.headers = headers;
        this.data = data;
        parseContentDisposition();
        parseContentType();
    }

    /**
     * Parse Content-Disposition header to extract name and filename.
     */
    private void parseContentDisposition() {
        String contentDisposition = headers.get("Content-Disposition");
        if (contentDisposition != null) {
            // Extract name
            String[] parts = contentDisposition.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("name=")) {
                    this.name = part.substring(6, part.length() - 1).replaceAll("\"", "");
                } else if (part.startsWith("filename=")) {
                    this.filename = part.substring(10, part.length() - 1).replaceAll("\"", "");
                }
            }
        }
    }

    /**
     * Parse Content-Type header.
     */
    private void parseContentType() {
        this.contentType = headers.get("Content-Type");
    }

    /**
     * Get the part name.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the filename (if this is a file upload).
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Get the content type.
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Get the part data.
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Get the part data as a string.
     */
    public String getDataAsString() {
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Check if this part is a file upload.
     */
    public boolean isFile() {
        return filename != null && !filename.isEmpty();
    }

    /**
     * Get all headers.
     */
    public Map<String, String> getHeaders() {
        return headers;
    }
}

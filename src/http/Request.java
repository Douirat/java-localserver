package http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

public class Request {

    private String method;
    private String path;
    private String version;

    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> queryParameters = new HashMap<>();
    // private final Map<String, String> pathVariables = new HashMap<>(); // TODO: add the path variables as well;

    private byte[] body;

    public Request() {

    }

    // --- controlled parsing API ---
    public void setRequestLine(String[] requestLine) {

        this.method = requestLine[0];
        this.path = requestLine[1];
        this.version = requestLine[2];

        if (this.path.contains("?")) {

            int ind = this.path.indexOf("?");

            String query = this.path.substring(ind + 1);
            this.path = this.path.substring(0, ind);

            String[] queryList = query.split("&");

            for (String q : queryList) {
                String[] qs = q.split("=");

                String key = qs[0];
                String value = qs.length > 1 ? qs[1] : "";

                this.queryParameters.put(key, value);
            }
        }
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public void addQueryParam(String key, String value) {
        queryParameters.put(key, value);
    }

    // --- GETTERS ---



    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Important: expose read-only view to avoid external mutation
     */
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public Map<String, String> getQueryParameters() {
        return Collections.unmodifiableMap(queryParameters);
    }

    // optional convenience method
    public String getHeader(String key) {
        return headers.get(key);
    }

    // Body comes after the header is handled:
    public void setBody(byte[] body) {
        this.body = body;
    }

    public byte[] getBody() {
        return body;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== HTTP REQUEST ===\n");

        sb.append("Method: ").append(method).append("\n");
        sb.append("Path: ").append(path).append("\n");
        sb.append("Version: ").append(version).append("\n");

        sb.append("\n--- Headers ---\n");
        if (headers.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                sb.append(entry.getKey())
                .append(": ")
                .append(entry.getValue())
                .append("\n");
            }
        }

        sb.append("\n--- Query Parameters ---\n");
        if (queryParameters.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
                sb.append(entry.getKey())
                .append("=")
                .append(entry.getValue())
                .append("\n");
            }
        }

           sb.append("\n--- Body ---\n");
           if(this.body == null){
            sb.append("(none)\n");
           }else if(this.body.length > 0){
            sb.append(new String(this.body, StandardCharsets.UTF_8));
           }

        sb.append("\n=== END REQUEST ===\n");

        return sb.toString();
    }
}
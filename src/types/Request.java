import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Request {

    private Socket socket;

    private String method;
    private String path;
    private String version;

    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> queryParameters = new HashMap<>();

    public Request(Socket socket) {
        this.socket = socket;
    }

    // --- controlled parsing API ---
    public void setRequestLine(String method, String path, String version) {
        this.method = method;
        this.path = path;
        this.version = version;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public void addQueryParam(String key, String value) {
        queryParameters.put(key, value);
    }

    // --- GETTERS ---

    public Socket getSocket() {
        return socket;
    }

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
}
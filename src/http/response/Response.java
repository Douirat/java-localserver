package http.response;

public class Response {
    private String version; // which HTTP Rules apply to parsing the response.
    private int status;
    private String statusMessage;

    private final Map<String, String> Headers = new HashMap<>();
}
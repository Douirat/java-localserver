package http;

import java.util.*;

public class Response {
    private String version; // which HTTP rules apply to parsing the response;
    private int status;
    private String statusReason; // the status line.
    private final Map<String, String> headers = new HashMap<>();
    private byte[] body;

    public Response(){}

    // Setters:
    public void setVersion(String version){this.version = version;}

    public void setStatus(int status){this.status = status;}

    public void setStatusReason(String statusReason){this.statusReason = statusReason;}

    public void setHeader(String key, String value){
        this.headers.put(key, value);
    }

    // FIXME: CONTEMPLATING THE BODY CONSTITUENTS:

}
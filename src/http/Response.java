package http;

import java.util.*;


public class Response {
    private String version; // which HTTP rules apply to parsing the response;
    private int status;
    private String statusReason; // the status line.
    private final Map<String, String> headers = new HashMap<>();
    private Object body;

    public Response(){}

    // Setters:
    public void setVersion(String version){this.version = version;}

    public void setStatus(int status){this.status = status;}

    public void setStatusReason(int statusReason){
        String message = HttpStatusMessages.getMessage(this.status);
        this.statusReason = message;
    }

    public void setHeader(String key, String value){
        this.headers.put(key, value);
    }

    public void setBody(Object body){this.body = body;}

// Getters:
    public String getVersion(){return this.version;}

    public int getStatus(){return this.status;}

    public String getStatusReason(){return this.statusReason;}

    public String getHeader(String key){return this.headers.get(key);}

    public Object getBody(){return this.body;}

    // toString method for debugging:
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("HTTP Response:\n");
        sb.append("Version: ").append(version).append("\n");
        sb.append("Status: ").append(status).append("\n");
        sb.append("Reason: ").append(statusReason).append("\n");

        sb.append("Headers:\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append("  ")
            .append(entry.getKey())
            .append(": ")
            .append(entry.getValue())
            .append("\n");
        }

        sb.append("Body:\n");
        if (body != null) {
            sb.append(body.toString());
        } else {
            sb.append("null");
        }

        return sb.toString();
    }

}
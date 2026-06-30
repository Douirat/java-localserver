package http.response;

import java.nio.channels.FileChannel;
import java.util.*;
import http.response.cookie.Cookie;
import http.response.responseBody.Body;
import http.response.responseBody.FileBody;
import http.response.responseBody.MemoryBody;
import http.response.status.HttpStatusMessages;

// TODO: CHANGING THE WAY THE BODY IS RETURNED TO AVOID ALWAYS HAVING THE BODY RETURENED IN THE MEMORY FILE


public class Response implements Responding {
    private String version; // which HTTP rules apply to parsing the response;
    private int status;
    private String statusReason; // the status line.
    private final Map<String, String> headers = new HashMap<>();
    private final List<Cookie> cookies = new ArrayList<>();
    private boolean isStaticResponse = false;

    private Body body;


    public Response(){}

    // Setters:
    public void setVersion(String version){this.version = version;}

   // Option A: derive reason automatically when status is set
    public void setStatus(int status) {
        this.status = status;
        this.statusReason = HttpStatusMessages.getMessage(status);
    }

    public void setHeader(String key, String value){
        this.headers.put(key, value);
    }

    public void addCookie(Cookie cookie){
        this.cookies.add(cookie);
    }

    public void SetAsStatic(){
        this.isStaticResponse = true;
    }

    public void setBody(Object data){
        MemoryBody body = new MemoryBody();
        body.setData(data);
        this.body = body;
    }

    public void setFileChannel(FileChannel channel){
        FileBody data = new FileBody();
        data.setChannel(channel);
        this.SetAsStatic();
        this.body = data;
    }

    // Getters:
    public String getVersion(){return this.version;}

    public int getStatus(){return this.status;}

    public String getStatusReason(){return this.statusReason;}

    public String getHeader(String key){return this.headers.get(key);}

   public List<Cookie> getCookies(){return Collections.unmodifiableList(cookies);}
   
   public boolean isStatic(){
    return this.isStaticResponse;
   }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public Object getBody(){
        MemoryBody object =(MemoryBody) this.body;
        return object.getData();
    }

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

        sb.append("Cookies:\n");
        for (Cookie cookie : cookies) {
            sb.append("  ")
            .append(cookie.getName())
            .append(": ")
            .append(cookie.getValue())
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
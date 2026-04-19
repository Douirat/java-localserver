package types;

import java.util.*;

public class URL{
    private String scheme;  // http, https (optional in raw HTTP request)
    private String host;    // from Host header
    private String path;    // /api/users
    private Map<String, String> queries;

    // No args constructor:
    public URL(){}

    // All args constructor:
    public URL(String scheme, String host, String path, Map<String, String> queries){
        this.scheme = scheme;
        this.host = host;
        this.path = path;
        this.queries = queries;
    }

    // Getters and setters
    public void setScheme(String scheme){
            this.scheme = scheme;
    }

    public void setHost(String host){
            this.host = host;
    }

    public void setPath(String path){
           this.path = path;
    }

    public void setQueries(Map<String, String> queries){
         this.queries = queries;
    }

    public String getScheme(){
        return this.scheme;
    }

    public String getHost(){
        return this.host;
    }

    public String getPath(){
        return this.path;
    }

    public Map<String, String> getQueries(){
        return this.queries;
    }

}
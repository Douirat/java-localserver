package types;

import java.util.*;

public class Request{
    private String  method;
    private URL url;
    private Map<String, String> headers;

    // No args constructor:
    public Request(){}

    // All args construtor:
    public Request(String  method, URL url, Map<String, String> headers){
        this.method = method;
        this.url = url;
        this.headers = headers;
    }

    // getters and setters:
    public void setMethod(String  method){
        this.method = method;
    }

    public void setURL(URL url){
        this.url = url;
    }

    public void setHeaders( Map<String, String> headers){
        this.headers = headers;
    }

    public String getMethod(){
        return  this.method;
    }

    public URL getURL(){
        return this.url;
    }

    public Map<String, String> getHeaders(){
        return this.headers;
    }
          
}
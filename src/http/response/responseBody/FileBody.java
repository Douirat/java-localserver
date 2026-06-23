package http.response.responseBody;

public class FileBody implements Body {
    public BodyType type(){
        return BodyType.MEMORY;
    }
}
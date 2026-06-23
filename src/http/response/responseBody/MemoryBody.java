package http.response.responseBody;

public class MemoryBody implements Body{
    public BodyType type(){
        return BodyType.MEMORY;
    }
}
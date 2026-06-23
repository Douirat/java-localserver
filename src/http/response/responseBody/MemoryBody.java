package http.response.responseBody;

public class MemoryBody implements Body{
    private Object data;
    public BodyType type(){
        return BodyType.MEMORY;
    }

    public void setData(Object data){
        this.data = data;
    }

    public Object getData(){
        return this.data;
    }
    
}
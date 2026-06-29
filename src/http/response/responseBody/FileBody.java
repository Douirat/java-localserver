package http.response.responseBody;

import java.nio.channels.FileChannel;

public class FileBody implements Body {
    private FileChannel channel;
    // private long size; optional cause i will form that before hand.
    // private String mimeType;
    
    public FileBody(){}
    public BodyType type(){
        return BodyType.FILE;
    }
    public void setChannel(FileChannel channel) {
        this.channel = channel;
    }
    public FileChannel getChannel() {
        return this.channel;
    }
}
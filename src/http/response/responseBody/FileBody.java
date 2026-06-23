package http.response.responseBody;

import java.nio.file.Path;

public class FileBody implements Body {
    private Path path;
    public FileBody(){}
    public BodyType type(){
        return BodyType.FILE;
    }
    public void setPath(Path path) {
        this.path = path;
    }
    public Path getPath() {
        return this.path;
    }
}
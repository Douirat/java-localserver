package http.router;

import java.io.IOException;
import java.nio.file.Path;

import http.handler.Handler;
import http.request.Request;
import http.response.Response;

public interface Routing {
    void addRoute(String method, String path, Handler handler);
    Response serve(Request request);
    Response serveFile(Path path)throws IOException;
    boolean isValidMethod(String method);
    boolean isValidPath(String method, String path);
    Handler matchRoute(Request request);
}
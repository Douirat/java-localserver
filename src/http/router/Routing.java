package http.router;

import http.handler.Handler;
import http.request.Request;
import http.response.Response;

public interface Routing {
    void addRoute(String method, String path, Handler handler);

    Response serve(Request request);

    boolean isValidMethod(String method);

    boolean isValidPath(String method, String path);

    Handler matchRoute(Request request);

    void setStaticDirectory(String route);

    String getStaticDirectory();
}
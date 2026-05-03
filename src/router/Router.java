package router;

import java.util.*;
import http.*;
import exceptions.*;


public class Router{
    // method => path => Response handle(Request req);
    Map<String, Map<String, Handler>> routes;

    // no args constructor:
    public Router(){
        routes = new HashMap<>();
        // Pre-create all HTTP method maps
        routes.put("GET", new HashMap<>());
        routes.put("POST", new HashMap<>());
        routes.put("PUT", new HashMap<>());
        routes.put("DELETE", new HashMap<>());
        routes.put("PATCH", new HashMap<>());
    }

// The API to expose to application to add routes: method => path ==> handler()!
    public void addRoute(String method, String path, Handler handler){
        
    if(method == null || !isValidMethod(method) || path == null || handler == null) {
        throw new IllegalRouteException("Invalid method or route or handler");
    }

    Map<String, Handler> routesMap = this.routes.get(method.toUpperCase());
    
    if ( routesMap == null) {
        throw new MethodException("Unsupported HTTP method: " + method);
    }

    if(routesMap.containsKey(path)) {
        throw new PathException("Route already exists: " + path);
    }

    routesMap.put(path, handler);
    }

// The API to expose to the server in order to pass in requests:
public Response serve(Request request){
    Map<String, Handler> routesMap = this.routes.get(request.getMethod().toUpperCase());
    Handler handler = routesMap.get(request.getPath());

    if (handler == null) {
        Response res = new Response();
        res.setStatus(404);
        res.setHeader("Content-Type", "text/plain");
        res.setBody("Route not found");
        return res;
    }
    return handler.handle(request);
}

// check a valid method:
public boolean isValidMethod(String method){
    return routes.containsKey(method.toUpperCase());
}

// use Regex expression to check valid path:
public boolean isValidPath(String method, String path){
    // Simple regex for path validation (can be expanded based on requirements)
    return path.matches("/[a-zA-Z0-9/_-]*");
}
}
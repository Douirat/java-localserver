package router;

import java.util.*;
import http.*;


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
    public void addRoute(String method, String route, Handler handler){
        Map<String, Handler> routesMap = this.routes.get(method.toUpperCase());
        // TODO: HANDLING ABSENCE ERROR: Error mechanism should be created:
        routesMap.put(route, handler);
    }

// The API to expose to the server in order to pass in requests:
public Response serve(Request request){
    Map<String, Handler> routesMap = this.routes.get(request.getMethod().toUpperCase());
    return routesMap.get(request.getPath()).handle(request);
}
}
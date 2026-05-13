package router;

import java.util.*;
import http.*;
import exceptions.*;
import java.util.regex.*;


public class Router{
    // method => path => Response handle(Request req);
    Map<String, Map<String, Handler>> routes;
    Map<String, Map<String, Handler>> dynamicRoutes; // for routes with path variables, e.g., /api/users/{id}>>
    
  

    // no args constructor:
    public Router(){
        routes = new HashMap<>();
        dynamicRoutes = new HashMap<>();
        // Pre-create all HTTP method maps
        routes.put("GET", new HashMap<>());
        dynamicRoutes.put("GET", new HashMap<>());
        routes.put("POST", new HashMap<>());
        dynamicRoutes.put("POST", new HashMap<>());
        routes.put("PUT", new HashMap<>());
        dynamicRoutes.put("PUT", new HashMap<>());
        routes.put("DELETE", new HashMap<>());
        dynamicRoutes.put("DELETE", new HashMap<>());
        routes.put("PATCH", new HashMap<>());
        dynamicRoutes.put("PATCH", new HashMap<>());
    }

// The API to expose to application to add routes: method => path ==> handler()!
    public void addRoute(String method, String path, Handler handler){
        
    if(method == null || !isValidMethod(method) || path == null || handler == null) {
        throw new IllegalRouteException("Invalid method or route or handler");
    }

    // handle dynamic route separately:
    if(path.contains("{") && path.contains("}")) {
        Map<String, Handler> dynamicRoutesMap = this.dynamicRoutes.get(method.toUpperCase());

        if (dynamicRoutesMap == null) {
            throw new MethodException("Unsupported HTTP method: " + method);
        }
        if(dynamicRoutesMap.containsKey(path)) {
            throw new PathException("Route already exists: " + path);
        }
        dynamicRoutesMap.put(path, handler);

    } else {
    Map<String, Handler> routesMap = this.routes.get(method.toUpperCase());
    
    if ( routesMap == null) {
        throw new MethodException("Unsupported HTTP method: " + method);
    }

    if(routesMap.containsKey(path)) {
        throw new PathException("Route already exists: " + path);
    }

    routesMap.put(path, handler);

    }
    }

// The API to expose to the server in order to pass in requests:
public Response serve(Request request){
    Handler handler = this.matchRoute(request);
    
    // if no handler found, return 404 response:
    if(handler == null) {
        Response response = new Response();
        response.setStatus(404);
        response.setHeader("Content-Type", "text/plain");
        response.setBody("Not Found");
        return response;
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

// create a route matching function that also extracts path variables for dynamic routes:
private Handler matchRoute(Request request) {
    // first check static routes:
    Map<String, Handler> routesMap = this.routes.get(request.getMethod().toUpperCase());

    if(routesMap != null) {
        Handler handler = routesMap.get(request.getPath());
        if (handler != null) {
            return handler;
        }
    }

    // then check dynamic routes:
    Map<String, Handler> dynamicRoutesMap = this.dynamicRoutes.get(request.getMethod().toUpperCase());

    if(dynamicRoutesMap != null) {
        for(Map.Entry<String, Handler> entry : dynamicRoutesMap.entrySet()) {
            String routePattern = entry.getKey();
            Handler handler = entry.getValue();

            // Convert route pattern to regex and extract variable names
            String regexPattern = routePattern.replaceAll("\\{[^/]+\\}", "([^/]+)");
            Pattern pattern = Pattern.compile(regexPattern);
            Matcher matcher = pattern.matcher(request.getPath());

            if (matcher.matches()) {
                // Extract path variables and add them to the request
                String[] variableNames = routePattern.split("/");
                int groupIndex = 1; // Start from 1 since group(0) is the entire match

                for (String segment : variableNames) {
                    if (segment.startsWith("{") && segment.endsWith("}")) {
                        String varName = segment.substring(1, segment.length() - 1);
                        String varValue = matcher.group(groupIndex++);
                        request.addPathVariable(varName, varValue);
                    }
                }
                return handler;
            }
        }
    }

    return null; // No matching route found
}

}
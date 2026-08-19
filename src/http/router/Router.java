package http.router;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import config.RouteConfig;
import config.ServerConfig;
import http.request.Request;
import http.response.Response;
import http.response.ResponseBuilder;

public class Router implements Routing {

    @Override
    public Response route(Request request, ServerConfig server) {
        RouteConfig route = resolveRoute(server, request.getPath());

        if (route == null) {
            return ResponseBuilder.notFound();
        }

        System.out.println("route ---> " + route.toString());

        if (!route.getMethods().isEmpty() && !route.getMethods().contains(request.getMethod())) {
            return ResponseBuilder.methodNotAllowed(String.join(", ", route.getMethods()));
        }

        if (route.getRedirectCode() > 0) {
            return ResponseBuilder.redirect(route.getRedirectCode(), route.getRedirectUrl());
        }

        return serveStatic(request, route);
    }

    // longest matching location prefix, e.g. "/api/users" beats "/api"
    public RouteConfig resolveRoute(ServerConfig server, String path) {
        RouteConfig best = null;
        int bestLen = -1;

        for (RouteConfig candidate : server.getRoutes()) {
            String prefix = candidate.getPath();
            boolean matches = path.equals(prefix) || path.startsWith(prefix.endsWith("/") ? prefix : prefix + "/");
            if (matches && prefix.length() > bestLen) {
                best = candidate;
                bestLen = prefix.length();
            }
        }

        return best;
    }

    private Response serveStatic(Request request, RouteConfig route) {
        String relative = request.getPath().substring(route.getPath().length());
        System.out.println("path---> " + relative);
        Path filePath = Path.of(route.getRoot(), relative);

        if (Files.isDirectory(filePath)) {
            if (route.getIndex() != null) {
                filePath = filePath.resolve(route.getIndex());
            } else {
                return route.isDirectoryListing() ? listDirectory(filePath) : ResponseBuilder.forbidden();
            }
        }

        if (!Files.exists(filePath)) {
            return ResponseBuilder.notFound();
        }

        try {
            // NOTE: reads the whole file into memory. Fine for small assets; for large
            // files this should stream via FileChannel.transferTo() from the write()
            // side in Server instead of buffering the full byte[] here.
            byte[] content = Files.readAllBytes(filePath);
            return ResponseBuilder.ok(content, contentType(filePath.toString()));
        } catch (IOException e) {
            return ResponseBuilder.internalServerError();
        }
    }

    private Response listDirectory(Path dir) {
        StringBuilder html = new StringBuilder("<html><body><ul>");
        try {
            Files.list(dir).forEach(p -> html.append("<li>").append(p.getFileName()).append("</li>"));
        } catch (IOException e) {
            return ResponseBuilder.internalServerError();
        }
        html.append("</ul></body></html>");
        return ResponseBuilder.ok(html.toString().getBytes(), "text/html");
    }

    private String contentType(String path) {
        if (path.endsWith(".html"))
            return "text/html";
        if (path.endsWith(".css"))
            return "text/css";
        if (path.endsWith(".js"))
            return "application/javascript";
        if (path.endsWith(".json"))
            return "application/json";
        if (path.endsWith(".png"))
            return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg"))
            return "image/jpeg";
        if (path.endsWith(".gif"))
            return "image/gif";
        if (path.endsWith(".svg"))
            return "image/svg+xml";
        if (path.endsWith(".ico"))
            return "image/x-icon";
        if (path.endsWith(".txt"))
            return "text/plain";
        return "application/octet-stream";
    }
}
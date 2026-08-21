package http.router;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import config.RouteConfig;
import config.ServerConfig;
import http.cgi.CGIHandler;
import http.request.Request;
import http.response.Response;
import http.response.ResponseBuilder;

public class Router implements Routing {

    @Override
    public Response route(Request request, ServerConfig server) {

        request.debug();

        RouteConfig route = resolveRoute(server, request.getPath());

        if (route == null) {
            return ResponseBuilder.notFound();
        }

        route.debug();

        if (!route.getMethods().isEmpty() && !route.getMethods().contains(request.getMethod())) {
            return ResponseBuilder.methodNotAllowed(String.join(", ", route.getMethods()));
        }

        if (route.getRedirectCode() > 0) {
            return ResponseBuilder.redirect(route.getRedirectCode(), route.getRedirectUrl());
        }

        if (!route.getCgiExtensions().isEmpty()) {
            return CGIHandler.execute(request, route);
        }

        // Handle POST (file upload)
        if (request.getMethod().equals("POST") && route.getUploadDir() != null) {
            return handleUpload(request, route);
        }

        // Handle DELETE
        if (request.getMethod().equals("DELETE")) {
            return handleDelete(request, route);
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
            long size = Files.size(filePath);
            FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ);
            return ResponseBuilder.okFile(contentType(filePath.toString()), size, fc);
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

    private Response handleUpload(Request request, RouteConfig route) {
        try {
            String contentType = request.getHeaders().get("content-type");
            if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                return ResponseBuilder.badRequest();
            }

            // Extract boundary
            String boundary = "--" + contentType.split("boundary=")[1].trim();

            byte[] body = request.getBody();
            String bodyStr = new String(body, java.nio.charset.StandardCharsets.ISO_8859_1);

            // Find filename
            int filenameIdx = bodyStr.indexOf("filename=\"");
            if (filenameIdx == -1) {
                return ResponseBuilder.badRequest();
            }
            int filenameStart = filenameIdx + 10;
            int filenameEnd = bodyStr.indexOf("\"", filenameStart);
            String filename = bodyStr.substring(filenameStart, filenameEnd);

            // Find start of file content (after the double CRLF following headers)
            int headerEnd = bodyStr.indexOf("\r\n\r\n", filenameIdx) + 4;

            // Find end boundary
            String endBoundary = boundary + "--";
            int contentEnd = bodyStr.lastIndexOf(endBoundary) - 2; // -2 for the CRLF before boundary

            // Extract raw bytes
            byte[] fileBytes = java.util.Arrays.copyOfRange(body, headerEnd, contentEnd);

            // Save to upload dir
            Path uploadPath = Path.of(route.getUploadDir(), filename);
            Files.write(uploadPath, fileBytes);

            return ResponseBuilder.ok(
                    ("File uploaded successfully: " + filename).getBytes(),
                    "text/plain");

        } catch (Exception e) {
            System.err.println("Upload error: " + e.getMessage());
            return ResponseBuilder.internalServerError();
        }
    }

    private Response handleDelete(Request request, RouteConfig route) {
        try {
            String relative = request.getPath().substring(route.getPath().length());
            if (relative.isEmpty() || relative.equals("/")) {
                return ResponseBuilder.badRequest();
            }

            Path filePath = Path.of(route.getRoot(), relative);

            if (!Files.exists(filePath)) {
                return ResponseBuilder.notFound();
            }

            if (Files.isDirectory(filePath)) {
                return ResponseBuilder.forbidden();
            }

            Files.delete(filePath);
            return ResponseBuilder.ok("File deleted successfully.".getBytes(), "text/plain");

        } catch (IOException e) {
            System.err.println("Delete error: " + e.getMessage());
            return ResponseBuilder.internalServerError();
        }
    }

}
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
import util.Cookie;
import util.Session;

public class Router {

    private static final String SESSION_ROUTE = "/session";

    public Response route(Request request, ServerConfig server) {

        // Built-in session demo endpoint — served before any config-file routes.
        if (request.getPath().equals(SESSION_ROUTE) || request.getPath().equals(SESSION_ROUTE + "/")) {
            return handleSessionEndpoint(request);
        }

        RouteConfig route = resolveRoute(server, request.getPath());

        if (route == null) {
            return ResponseBuilder.notFound(server);
        }

        if (server != null && server.getMaxBodyBytes() > 0 && request.getBody() != null
                && request.getBody().length > server.getMaxBodyBytes()) {
            return ResponseBuilder.payloadTooLarge(server);
        }

        if (!route.getMethods().isEmpty() && !route.getMethods().contains(request.getMethod())) {
            return ResponseBuilder.methodNotAllowed(String.join(", ", route.getMethods()), server);
        }

        if (route.getRedirectCode() > 0) {
            return ResponseBuilder.redirect(route.getRedirectCode(), route.getRedirectUrl());
        }

        if (!route.getCgiExtensions().isEmpty()) {
            return CGIHandler.execute(request, route, server);
        }

        // Handle POST (file upload)
        if (request.getMethod().equals("POST") && route.getUploadDir() != null) {
            return handleUpload(request, route, server);
        }

        // Handle DELETE
        if (request.getMethod().equals("DELETE")) {
            return handleDelete(request, route, server);
        }

        return serveStatic(request, route, server);
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

    private Response serveStatic(Request request, RouteConfig route, ServerConfig server) {
        String relative = request.getPath().substring(route.getPath().length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path root = Path.of(route.getRoot()).toAbsolutePath().normalize();
        Path filePath = root.resolve(relative).normalize();

        // Path traversal protection: ensure resolved path is within root
        if (!filePath.startsWith(root)) {
            return ResponseBuilder.forbidden(server);
        }

        if (Files.isDirectory(filePath)) {
            if (route.getIndex() != null) {
                filePath = filePath.resolve(route.getIndex()).normalize();
                if (!filePath.startsWith(root)) {
                    return ResponseBuilder.forbidden(server);
                }
            } else {
                return route.isDirectoryListing() ? listDirectory(filePath, server) : ResponseBuilder.forbidden(server);
            }
        }

        if (!Files.exists(filePath)) {
            return ResponseBuilder.notFound(server);
        }

        try {
            long size = Files.size(filePath);
            FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ);
            return ResponseBuilder.okFile(contentType(filePath.toString()), size, fc);
        } catch (IOException e) {
            return ResponseBuilder.internalServerError(server);
        }
    }

    private Response listDirectory(Path dir, ServerConfig server) {
        StringBuilder html = new StringBuilder("<html><body><ul>");
        try {
            Files.list(dir).forEach(p -> html.append("<li><a href=\"").append(p.getFileName()).append("\">")
                    .append(p.getFileName()).append("</a></li>"));
        } catch (IOException e) {
            return ResponseBuilder.internalServerError(server);
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

    private Response handleUpload(Request request, RouteConfig route, ServerConfig server) {
        try {
            String contentType = request.getHeaders().get("content-type");
            if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                return ResponseBuilder.badRequest(server);
            }

            // Extract boundary
            String boundary = "--" + contentType.split("boundary=")[1].trim();

            byte[] body = request.getBody();
            String bodyStr = new String(body, java.nio.charset.StandardCharsets.ISO_8859_1);

            // Find filename
            int filenameIdx = bodyStr.indexOf("filename=\"");
            if (filenameIdx == -1) {
                return ResponseBuilder.badRequest(server);
            }
            int filenameStart = filenameIdx + 10;
            int filenameEnd = bodyStr.indexOf("\"", filenameStart);
            if (filenameEnd == -1) {
                return ResponseBuilder.badRequest(server);
            }
            String rawFilename = bodyStr.substring(filenameStart, filenameEnd).trim();
            if (rawFilename.isEmpty()) {
                return ResponseBuilder.badRequest(server);
            }

            // Extract bare filename to prevent path traversal
            Path rawPath = Path.of(rawFilename).getFileName();
            if (rawPath == null) {
                return ResponseBuilder.badRequest(server);
            }
            String filename = rawPath.toString();
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return ResponseBuilder.badRequest(server);
            }

            // Find start of file content (after the double CRLF following headers)
            int headerEnd = bodyStr.indexOf("\r\n\r\n", filenameIdx) + 4;

            // Find end boundary
            String endBoundary = boundary + "--";
            int contentEnd = bodyStr.lastIndexOf(endBoundary) - 2; // -2 for the CRLF before boundary

            if (headerEnd < 4 || contentEnd < headerEnd) {
                return ResponseBuilder.badRequest(server);
            }

            // Extract raw bytes
            byte[] fileBytes = java.util.Arrays.copyOfRange(body, headerEnd, contentEnd);

            // Save to upload dir and ensure it stays inside uploadDir
            Path uploadDir = Path.of(route.getUploadDir()).toAbsolutePath().normalize();
            Path uploadPath = uploadDir.resolve(filename).normalize();
            if (!uploadPath.startsWith(uploadDir) || !uploadPath.getParent().equals(uploadDir)) {
                return ResponseBuilder.forbidden(server);
            }

            Files.write(uploadPath, fileBytes);

            return ResponseBuilder.ok(
                    ("File uploaded successfully: " + filename).getBytes(),
                    "text/plain");

        } catch (Exception e) {
            System.err.println("Upload error: " + e.getMessage());
            return ResponseBuilder.internalServerError(server);
        }
    }

    private Response handleDelete(Request request, RouteConfig route, ServerConfig server) {
        try {
            String relative = request.getPath().substring(route.getPath().length());
            while (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            if (relative.isEmpty()) {
                return ResponseBuilder.badRequest(server);
            }

            Path root = Path.of(route.getRoot()).toAbsolutePath().normalize();
            Path filePath = root.resolve(relative).normalize();

            // Prevent path traversal outside root, or attempting to delete the root directory itself
            if (!filePath.startsWith(root) || filePath.equals(root)) {
                return ResponseBuilder.forbidden(server);
            }

            if (!Files.exists(filePath)) {
                return ResponseBuilder.notFound(server);
            }

            if (Files.isDirectory(filePath)) {
                return ResponseBuilder.forbidden(server);
            }

            Files.delete(filePath);
            return ResponseBuilder.ok("File deleted successfully.".getBytes(), "text/plain");

        } catch (IOException e) {
            System.err.println("Delete error: " + e.getMessage());
            return ResponseBuilder.internalServerError(server);
        }
    }

    // -----------------------------------------------------------------------
    // Built-in /session endpoint for audit demonstration
    // -----------------------------------------------------------------------
    private Response handleSessionEndpoint(Request request) {
        if ("DELETE".equals(request.getMethod())) {
            Session existing = request.getSession(false);
            if (existing != null) {
                Session.invalidate(existing.getId());
            }
            Response resp = ResponseBuilder.ok("Session invalidated".getBytes(), "text/plain");
            resp.addCookie(Cookie.of(Session.COOKIE_NAME, "").setPath("/").setMaxAge(0));
            return resp;
        }

        Session session = request.getSession(true);
        int visits = 1;
        Object prev = session.getAttribute("visits");
        if (prev instanceof Integer) {
            visits = (Integer) prev + 1;
        }
        session.setAttribute("visits", visits);

        String html = "<!DOCTYPE html><html><body>"
                + "<h1>Session & Cookie Demo</h1>"
                + "<p><b>Session ID:</b> " + session.getId() + "</p>"
                + "<p><b>Visits:</b> " + visits + "</p>"
                + "<p><b>Active Sessions:</b> " + Session.getActiveCount() + "</p>"
                + "<p>Refresh this page to increment the visit counter!</p>"
                + "</body></html>";

        Response resp = ResponseBuilder.ok(html.getBytes(), "text/html");
        resp.addCookie(Cookie.of(Session.COOKIE_NAME, session.getId()).setPath("/").setHttpOnly(true));
        return resp;
    }

}
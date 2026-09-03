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
import util.SessionManager;

public class Router implements Routing {

    private static final String SESSION_ROUTE = "/session";

    @Override
    public Response route(Request request, ServerConfig server) {

        request.debug();

        // Built-in session demo endpoint — served before any config-file routes.
        if (request.getPath().equals(SESSION_ROUTE) || request.getPath().equals(SESSION_ROUTE + "/")) {
            return handleSessionEndpoint(request);
        }

        RouteConfig route = resolveRoute(server, request.getPath());

        if (route == null) {
            return ResponseBuilder.notFound(server);
        }

        route.debug();

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
        Path filePath = Path.of(route.getRoot(), relative);

        if (Files.isDirectory(filePath)) {
            if (route.getIndex() != null) {
                filePath = filePath.resolve(route.getIndex());
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

            Path filePath = Path.of(route.getRoot(), relative);

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
    // Built-in /session endpoint
    // Demonstrates working session and cookie management for the audit.
    //
    // GET  /session          — show session info + visit counter
    // POST /session          — set a named attribute ("name" query param)
    // DELETE /session        — invalidate the session
    // -----------------------------------------------------------------------
    private Response handleSessionEndpoint(Request request) {
        String method = request.getMethod();

        // ---- DELETE: invalidate session ----
        if ("DELETE".equals(method)) {
            Session existing = request.getSession(false);
            String id = (existing != null) ? existing.getId() : null;
            if (id != null) {
                SessionManager.getInstance().invalidateSession(id);
            }
            Response resp = ResponseBuilder.create()
                    .status(200, "OK")
                    .header("Content-Type", "text/html")
                    .body("<html><body><h1>Session invalidated</h1></body></html>")
                    .build();
            // Clear the cookie by setting Max-Age=0
            resp.addCookie(
                Cookie.of(SessionManager.DEFAULT_SESSION_COOKIE_NAME, "")
                    .setPath("/")
                    .setMaxAge(0)
                    .setHttpOnly(true)
            );
            return resp;
        }

        // ---- POST: set "name" attribute on session ----
        if ("POST".equals(method)) {
            Session session = request.getSession(true); // create if absent
            String name = request.getQueryParameters().getOrDefault("name", "anonymous");
            session.setAttribute("name", name);
            session.setAttribute("lastSet", String.valueOf(System.currentTimeMillis()));

            Response resp = ResponseBuilder.create()
                    .status(200, "OK")
                    .header("Content-Type", "text/html")
                    .body(buildSessionPage(session, "Attribute 'name' set to: " + name))
                    .build();
            ensureSessionCookie(resp, session);
            return resp;
        }

        // ---- GET: show session info / visit counter ----
        Session session = request.getSession(true); // create if absent
        int visits = 1;
        Object prev = session.getAttribute("visits");
        if (prev instanceof Integer) {
            visits = (Integer) prev + 1;
        }
        session.setAttribute("visits", visits);

        Response resp = ResponseBuilder.create()
                .status(200, "OK")
                .header("Content-Type", "text/html")
                .body(buildSessionPage(session, "Visit #" + visits))
                .build();
        ensureSessionCookie(resp, session);
        return resp;
    }

    // Adds a Set-Cookie header for the session ID only when the session is new
     
    private void ensureSessionCookie(Response response, Session session) {
        Cookie sessionCookie = Cookie.of(SessionManager.DEFAULT_SESSION_COOKIE_NAME, session.getId())
                .setPath("/")
                .setMaxAge(session.getMaxInactiveInterval())
                .setHttpOnly(true)
                .setSameSite("Lax");
        response.addCookie(sessionCookie);
    }

    // Build a simple HTML page showing the current session's state.
    private String buildSessionPage(Session session, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><title>Session Demo</title></head><body>");
        sb.append("<h1>Session Demo</h1>");
        sb.append("<p><strong>").append(message).append("</strong></p>");
        sb.append("<hr>");
        sb.append("<p><b>Session ID:</b> ").append(session.getId()).append("</p>");
        sb.append("<p><b>Created at:</b> ").append(new java.util.Date(session.getCreatedAt())).append("</p>");
        sb.append("<p><b>Last accessed:</b> ").append(new java.util.Date(session.getLastAccessedAt())).append("</p>");
        sb.append("<p><b>Active sessions:</b> ").append(SessionManager.getInstance().getActiveSessionCount()).append("</p>");
        sb.append("<h3>Attributes:</h3><ul>");
        for (String key : session.getAttributeNames()) {
            sb.append("<li><b>").append(key).append("</b>: ").append(session.getAttribute(key)).append("</li>");
        }
        sb.append("</ul>");
        sb.append("<hr>");
        sb.append("<p>Refresh this page to increment the visit counter.</p>");
        sb.append("<form method='POST' action='/session?name=YourName'><button>Set 'name' attribute</button></form>");
        sb.append("<form method='POST' action='/session?name=' onsubmit=\"this.action='/session?name='+document.getElementById('n').value\">");
        sb.append("  <input id='n' type='text' placeholder='Enter name'>");
        sb.append("  <button type='submit'>Set custom name</button>");
        sb.append("</form>");
        sb.append("<br><a href='/session'>Refresh</a>");
        sb.append("</body></html>");
        return sb.toString();
    }

}
package http.router;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import http.request.Request;
import http.response.Response;
import http.response.ResponseBuilder;
import http.response.responseBody.FileBody;
import http.handler.Handler;
import java.util.regex.*;

public class Router implements Routing {

    // method => path => Response handle(Request req);
    Map<String, Map<String, Handler>> routes;
    Map<String, Map<String, Handler>> dynamicRoutes; // for routes with path variables, e.g., /api/users/{id}>>

    private String staticDirectory;
    private String defaultFile = "index.html";
    private int maxBodySize = 10485760; // 10MB default
    private boolean directoryListing = false;
    private Map<String, String> errorPages = new HashMap<>();
    private Map<String, String> cgiExtensions = new HashMap<>();
    private Map<String, RedirectConfig> redirects = new HashMap<>();

    // Inner class for redirect configuration
    private static class RedirectConfig {
        String targetPath;
        int statusCode;

        RedirectConfig(String targetPath, int statusCode) {
            this.targetPath = targetPath;
            this.statusCode = statusCode;
        }
    }

    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            // HTML / Text
            Map.entry("html", "text/html; charset=UTF-8"),
            Map.entry("htm", "text/html; charset=UTF-8"),
            Map.entry("txt", "text/plain; charset=UTF-8"),
            Map.entry("css", "text/css; charset=UTF-8"),
            Map.entry("csv", "text/csv; charset=UTF-8"),

            // JavaScript / JSON / XML
            Map.entry("js", "application/javascript; charset=UTF-8"),
            Map.entry("mjs", "application/javascript; charset=UTF-8"),
            Map.entry("json", "application/json; charset=UTF-8"),
            Map.entry("xml", "application/xml; charset=UTF-8"),

            // Images
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"),

            // Audio
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("aac", "audio/aac"),
            Map.entry("flac", "audio/flac"),

            // Video
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("ogv", "video/ogg"),
            Map.entry("avi", "video/x-msvideo"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("mkv", "video/x-matroska"),

            // Documents
            Map.entry("pdf", "application/pdf"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("doc", "application/msword"),
            Map.entry(
                    "docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry(
                    "xls",
                    "application/vnd.ms-excel"),
            Map.entry(
                    "xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry(
                    "ppt",
                    "application/vnd.ms-powerpoint"),
            Map.entry(
                    "pptx",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"),

            // Archives
            Map.entry("zip", "application/zip"),
            Map.entry("gz", "application/gzip"),
            Map.entry("tar", "application/x-tar"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("7z", "application/x-7z-compressed"),

            // Fonts
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),

            // WebAssembly
            Map.entry("wasm", "application/wasm"));

    // no args constructor:
    public Router() {
        this.staticDirectory = "static";
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
    @Override
    public void addRoute(String method, String path, Handler handler) {

        if (method == null || !isValidMethod(method) || path == null || handler == null) {
            throw new RuntimeException("Invalid method or route or handler");
        }

        // handle dynamic route separately:
        if (path.contains("{") && path.contains("}")) {
            Map<String, Handler> dynamicRoutesMap = this.dynamicRoutes.get(method.toUpperCase());

            if (dynamicRoutesMap == null) {
                throw new RuntimeException("Unsupported HTTP method: " + method);
            }

            if (dynamicRoutesMap.containsKey(path)) {
                throw new RuntimeException("Route already exists: " + path);
            }

            dynamicRoutesMap.put(path, handler);

        } else {

            Map<String, Handler> routesMap = this.routes.get(method.toUpperCase());

            if (routesMap == null) {
                throw new RuntimeException("Unsupported HTTP method: " + method);
            }

            if (routesMap.containsKey(path)) {
                throw new RuntimeException("Route already exists: " + path);
            }

            routesMap.put(path, handler);

        }
    }

    // The API to expose to the server in order to pass in requests:
    @Override
    public Response serve(Request request) {

        // Check for redirects first
        RedirectConfig redirect = this.redirects.get(request.getPath());
        if (redirect != null) {
            return new ResponseBuilder()
                    .setStatus(redirect.statusCode)
                    .setHeader("Location", redirect.targetPath)
                    .build();
        }

        if (request.getMethod().equals("GET")) {
            System.out.println("Request path: " + request.getPath());
            String prefix = "/" + this.staticDirectory + "/";

            System.out.println("staticDirectory = [" + this.staticDirectory + "]");
            System.out.println("prefix          = [" + prefix + "]");
            System.out.println("requestPath     = [" + request.getPath() + "]");
            System.out.println(
                    request.getPath().startsWith(prefix));

            if (request.getPath().startsWith(prefix)) {
                try {
                    String relative = request.getPath()
                            .substring(prefix.length());

                    Path requested = Paths.get(this.staticDirectory)
                            .resolve(relative);

                    System.out.println("serving static file: " + requested.toAbsolutePath());

                    return this.serveFile(requested);

                } catch (IOException e) {
                    return new ResponseBuilder()
                            .setStatus(500)
                            .build();
                }
            }
        }
        Handler handler = this.matchRoute(request);

        // if no handler found, return 404 response:
        if (handler == null) {
            Response response = new ResponseBuilder()
                    .setStatus(404)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("Not Found")
                    .build();
            return response;
        }

        return handler.handle(request);
    }

    // Serve a static file from the configured static directory.
    public Response serveFile(Path requested)
            throws IOException {

        Path path = requested
                .toAbsolutePath()
                .normalize();

        System.out.println("requested = " + requested);
        System.out.println("path      = " + path);
        System.out.println("staticDir = " + this.staticDirectory);
        System.out.println("exists    = " + Files.exists(path));

        // Prevent escaping the static directory.
        Path staticRoot = Paths.get(this.staticDirectory)
                .toAbsolutePath()
                .normalize();

        if (!path.startsWith(staticRoot)) {
            return new ResponseBuilder()
                    .setStatus(403)
                    .build();
        }

        // File does not exist.
        if (!Files.exists(path)) {
            return new ResponseBuilder()
                    .setStatus(404)
                    .build();
        }

        // Not a regular file (directory, device, etc.).
        if (!Files.isRegularFile(path)) {
            return new ResponseBuilder()
                    .setStatus(404)
                    .build();
        }

        // Exists but cannot be read.
        if (!Files.isReadable(path)) {
            return new ResponseBuilder()
                    .setStatus(403)
                    .build();
        }

        FileChannel fc = null;

        try {
            fc = FileChannel.open(
                    path,
                    StandardOpenOption.READ);

            long size = fc.size();
            String mime = mimeToContentType(path);

            FileBody body = new FileBody();
            body.setChannel(fc);

            return new ResponseBuilder()
                    .setStatus(200)
                    .setHeader(
                            "Content-Length",
                            Long.toString(size))
                    .setHeader(
                            "Content-Type",
                            mime)
                    .setAsStatic()
                    .setBody(body)
                    .build();

        } catch (Exception e) {
            if (fc != null) {
                fc.close();
            }
            throw e;
        }
    }

    // check a valid method:
    @Override
    public boolean isValidMethod(String method) {
        return routes.containsKey(method.toUpperCase());
    }

    // use Regex expression to check valid path:
    @Override
    public boolean isValidPath(String method, String path) {
        // Simple regex for path validation (can be expanded based on requirements)
        return path.matches("/[a-zA-Z0-9/_-]*");
    }

    // create a route matching function that also extracts path variables for
    // dynamic routes:
    @Override
    public Handler matchRoute(Request request) {
        // first check static routes:
        Map<String, Handler> routesMap = this.routes.get(request.getMethod().toUpperCase());

        if (routesMap != null) {
            Handler handler = routesMap.get(request.getPath());
            if (handler != null) {
                return handler;
            }

            // for (var entry : routesMap.entrySet()) {
            // String route = entry.getKey();

            // if (route.equals(this.staticDirectory)
            // && request.getPath().startsWith("/static/")) {
            // return entry.getValue();
            // }
            // }

        }

        // then check dynamic routes:
        Map<String, Handler> dynamicRoutesMap = this.dynamicRoutes.get(request.getMethod().toUpperCase());

        if (dynamicRoutesMap != null) {
            for (Map.Entry<String, Handler> entry : dynamicRoutesMap.entrySet()) {
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

    @Override
    public void setStaticDirectory(String route) {
        this.staticDirectory = route;
    }

    @Override
    public String getStaticDirectory() {
        return this.staticDirectory;
    }

    // Configuration methods for ConfigLoader
    public void setDefaultFile(String defaultFile) {
        this.defaultFile = defaultFile;
    }

    public void setMaxBodySize(int maxSize) {
        this.maxBodySize = maxSize;
    }

    public void setDirectoryListing(boolean enabled) {
        this.directoryListing = enabled;
    }

    public void setErrorPage(String statusCode, String path) {
        this.errorPages.put(statusCode, path);
    }

    public void addCgiExtension(String extension, String interpreter) {
        this.cgiExtensions.put(extension, interpreter);
    }

    public void addRedirect(String path, String redirect, int statusCode) {
        this.redirects.put(path, new RedirectConfig(redirect, statusCode));
    }

    public String getDefaultFile() {
        return defaultFile;
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    public boolean isDirectoryListing() {
        return directoryListing;
    }

    public Map<String, String> getErrorPages() {
        return errorPages;
    }

    public Map<String, String> getCgiExtensions() {
        return cgiExtensions;
    }

    public Map<String, RedirectConfig> getRedirects() {
        return redirects;
    }

    /**
     * Create a method that:
     * 
     * @param Path of a file;
     *             and based on the mime it will
     * @return image/png
     *         image/jpeg
     *         text/html
     *         text/css
     *         application/javascript
     *         video/mp4
     *         application/pdf
     */

    public static String mimeToContentType(Path path) {
        String file = path.getFileName().toString();

        int dot = file.lastIndexOf('.');

        String ext = dot == -1
                ? ""
                : file.substring(dot + 1);

        String mime = MIME_TYPES.getOrDefault(
                ext,
                "application/octet-stream");

        return mime;
    }

}
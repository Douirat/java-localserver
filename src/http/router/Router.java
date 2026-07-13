package http.router;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private Map<String, String> virtualHosts = new HashMap<>(); // hostname -> static directory
    private Map<String, Set<String>> routeMethodRestrictions = new HashMap<>(); // path -> allowed methods

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

        // Check method restrictions
        if (!isMethodAllowed(request.getPath(), request.getMethod())) {
            Set<String> allowedMethods = getAllowedMethods(request.getPath());
            String allowHeader = String.join(", ", allowedMethods);
            Response errResponse = buildErrorResponse(405, "Method Not Allowed");
            errResponse.getHeaders().put("Allow", allowHeader);
            return errResponse;
        }

        // Determine static directory based on virtual host
        String host = request.getHeader("Host");
        String currentStaticDirectory = this.staticDirectory;
        if (host != null && this.virtualHosts.containsKey(host)) {
            currentStaticDirectory = this.virtualHosts.get(host);
            System.out.println("Using virtual host directory: " + currentStaticDirectory + " for host: " + host);
        }

        // Check for CGI execution
        if (request.getPath().startsWith("/cgi-bin/")) {
            String scriptName = request.getPath().substring("/cgi-bin/".length());
            String pyExec = this.cgiExtensions.getOrDefault("py", "python");
            String plExec = this.cgiExtensions.getOrDefault("pl", "perl");
            http.cgi.CgiHandler cgiHandler = new http.cgi.CgiHandler("cgi-bin", pyExec, plExec);
            return cgiHandler.executeScript(scriptName, request);
        }

        if (request.getMethod().equals("GET")) {
            System.out.println("Request path: " + request.getPath());
            String prefix = "/" + currentStaticDirectory + "/";

            System.out.println("staticDirectory = [" + currentStaticDirectory + "]");
            System.out.println("prefix          = [" + prefix + "]");
            System.out.println("requestPath     = [" + request.getPath() + "]");
            System.out.println(
                    request.getPath().startsWith(prefix));

            if (request.getPath().startsWith(prefix)) {
                try {
                    String relative = request.getPath()
                            .substring(prefix.length());

                    Path requested = Paths.get(currentStaticDirectory)
                            .resolve(relative);

                    System.out.println("serving static file: " + requested.toAbsolutePath());

                    return this.serveFile(requested);

                } catch (IOException e) {
                    return buildErrorResponse(500, "Internal Server Error");
                }
            }
        }
        Handler handler = this.matchRoute(request);

        // if no handler found, return 404 response:
        if (handler == null) {
            return buildErrorResponse(404, "Not Found");
        }

        return handler.handle(request);
    }

    /**
     * Build an error response using custom error pages if configured.
     */
    private Response buildErrorResponse(int statusCode, String defaultMessage) {
        String errorPagePath = this.errorPages.get(String.valueOf(statusCode));
        
        if (errorPagePath != null) {
            try {
                Path errorFilePath = Paths.get(errorPagePath);
                if (Files.exists(errorFilePath) && Files.isRegularFile(errorFilePath) && Files.isReadable(errorFilePath)) {
                    return serveFile(errorFilePath);
                }
            } catch (IOException e) {
                System.err.println("Error serving custom error page: " + e.getMessage());
            }
        }
        
        // Fallback to default error response
        return new ResponseBuilder()
                .setStatus(statusCode)
                .setHeader("Content-Type", "text/plain")
                .setBody(defaultMessage)
                .build();
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
            return buildErrorResponse(403, "Forbidden");
        }

        // File does not exist.
        if (!Files.exists(path)) {
            return buildErrorResponse(404, "Not Found");
        }

        // Not a regular file - check if it's a directory
        if (!Files.isRegularFile(path)) {
            if (Files.isDirectory(path)) {
                // Try to serve default file (index.html)
                Path defaultFile = path.resolve(this.defaultFile);
                if (Files.exists(defaultFile) && Files.isRegularFile(defaultFile) && Files.isReadable(defaultFile)) {
                    return serveFile(defaultFile);
                }
                
                // If default file not found and directory listing is enabled, generate listing
                if (this.directoryListing) {
                    return generateDirectoryListing(path);
                }
                
                // Directory listing disabled and no default file
                return buildErrorResponse(403, "Directory listing disabled");
            }
            
            // Not a directory and not a regular file
            return buildErrorResponse(404, "Not Found");
        }

        // Exists but cannot be read.
        if (!Files.isReadable(path)) {
            return buildErrorResponse(403, "Forbidden");
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

    // Virtual host management
    public void addVirtualHost(String hostname, String staticDirectory) {
        this.virtualHosts.put(hostname, staticDirectory);
    }

    public String getVirtualHostDirectory(String hostname) {
        return this.virtualHosts.get(hostname);
    }

    public Map<String, String> getVirtualHosts() {
        return virtualHosts;
    }

    // Route method restrictions
    public void addAllowedMethod(String path, String method) {
        routeMethodRestrictions.computeIfAbsent(path, k -> new HashSet<>()).add(method.toUpperCase());
    }

    public Set<String> getAllowedMethods(String path) {
        return routeMethodRestrictions.get(path);
    }

    public boolean isMethodAllowed(String path, String method) {
        Set<String> allowedMethods = routeMethodRestrictions.get(path);
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            return true; // No restrictions, all methods allowed
        }
        return allowedMethods.contains(method.toUpperCase());
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

    /**
     * Generate an HTML directory listing for the given path.
     */
    private Response generateDirectoryListing(Path directoryPath) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<title>Directory Listing: ").append(directoryPath.getFileName()).append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 40px; }\n");
        html.append("h1 { color: #333; }\n");
        html.append("table { border-collapse: collapse; width: 100%; }\n");
        html.append("th, td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }\n");
        html.append("th { background-color: #f2f2f2; }\n");
        html.append("a { color: #0066cc; text-decoration: none; }\n");
        html.append("a:hover { text-decoration: underline; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        html.append("<h1>Directory Listing: ").append(directoryPath.getFileName()).append("</h1>\n");
        html.append("<table>\n");
        html.append("<tr><th>Name</th><th>Size</th><th>Last Modified</th></tr>\n");

        // Add parent directory link if not at root
        if (!directoryPath.equals(Paths.get(this.staticDirectory).toAbsolutePath().normalize())) {
            html.append("<tr><td><a href=\"../\">../</a></td><td>-</td><td>-</td></tr>\n");
        }

        try {
            Files.list(directoryPath).forEach(path -> {
                try {
                    String name = path.getFileName().toString();
                    String size = Files.isDirectory(path) ? "-" : String.valueOf(Files.size(path));
                    String modified = Files.getLastModifiedTime(path).toString();
                    String href = Files.isDirectory(path) ? name + "/" : name;

                    html.append("<tr>");
                    html.append("<td><a href=\"").append(href).append("\">").append(name).append("</a></td>");
                    html.append("<td>").append(size).append("</td>");
                    html.append("<td>").append(modified).append("</td>");
                    html.append("</tr>\n");
                } catch (IOException e) {
                    html.append("<tr><td>").append(path.getFileName()).append("</td><td>-</td><td>Error</td></tr>\n");
                }
            });
        } catch (IOException e) {
            html.append("<tr><td colspan=\"3\">Error reading directory</td></tr>\n");
        }

        html.append("</table>\n");
        html.append("</body>\n</html>");

        return new ResponseBuilder()
                .setStatus(200)
                .setHeader("Content-Type", "text/html")
                .setBody(html.toString().getBytes(StandardCharsets.UTF_8))
                .build();
    }

}
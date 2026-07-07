package http.cgi;

import http.request.Request;
import http.response.Response;
import http.response.ResponseBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles CGI (Common Gateway Interface) execution for Python scripts.
 */
public class CgiHandler {

    private final String cgiDirectory;
    private final String pythonExecutable;

    public CgiHandler(String cgiDirectory) {
        this(cgiDirectory, "python"); // Default to 'python' in PATH
    }

    public CgiHandler(String cgiDirectory, String pythonExecutable) {
        this.cgiDirectory = cgiDirectory;
        this.pythonExecutable = pythonExecutable;
    }

    /**
     * Execute a CGI script and return the response.
     */
    public Response executeScript(String scriptName, Request request) {
        Path scriptPath = Paths.get(cgiDirectory, scriptName).normalize();
        
        // Security check: ensure script is within CGI directory
        Path cgiPath = Paths.get(cgiDirectory).toAbsolutePath().normalize();
        if (!scriptPath.startsWith(cgiPath)) {
            return new ResponseBuilder()
                    .setStatus(403)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("Access denied: Script path outside CGI directory")
                    .build();
        }

        if (!Files.exists(scriptPath) || !Files.isRegularFile(scriptPath)) {
            return new ResponseBuilder()
                    .setStatus(404)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("Script not found")
                    .build();
        }

        if (!Files.isReadable(scriptPath)) {
            return new ResponseBuilder()
                    .setStatus(403)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("Script not readable")
                    .build();
        }

        try {
            // Build environment variables
            Map<String, String> env = new HashMap<>(System.getenv());
            env.putAll(buildCgiEnvironment(request, scriptPath));

            // Build command
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, scriptPath.toString());
            pb.environment().putAll(env);

            // Redirect error stream to output stream
            pb.redirectErrorStream(true);

            // Execute
            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                return new ResponseBuilder()
                        .setStatus(500)
                        .setHeader("Content-Type", "text/plain")
                        .setBody("CGI script error (exit code " + exitCode + "):\n" + output.toString())
                        .build();
            }

            // Parse CGI output (headers and body)
            return parseCgiOutput(output.toString());

        } catch (IOException e) {
            return new ResponseBuilder()
                    .setStatus(500)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("Error executing CGI script: " + e.getMessage())
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResponseBuilder()
                    .setStatus(500)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("CGI execution interrupted")
                    .build();
        }
    }

    /**
     * Build CGI environment variables from the HTTP request.
     */
    private Map<String, String> buildCgiEnvironment(Request request, Path scriptPath) {
        Map<String, String> env = new HashMap<>();

        // Server variables
        env.put("SERVER_SOFTWARE", "JavaLocalServer/1.0");
        env.put("SERVER_NAME", request.getHeader("Host") != null ? 
                request.getHeader("Host").split(":")[0] : "localhost");
        env.put("GATEWAY_INTERFACE", "CGI/1.1");

        // Request variables
        env.put("REQUEST_METHOD", request.getMethod());
        env.put("PATH_INFO", request.getPath());
        
        // Build query string from query parameters
        StringBuilder queryString = new StringBuilder();
        for (Map.Entry<String, String> entry : request.getQueryParameters().entrySet()) {
            if (queryString.length() > 0) {
                queryString.append("&");
            }
            queryString.append(entry.getKey()).append("=").append(entry.getValue());
        }
        env.put("QUERY_STRING", queryString.toString());
        
        env.put("CONTENT_TYPE", request.getHeader("Content-Type") != null ? 
                request.getHeader("Content-Type") : "");
        env.put("CONTENT_LENGTH", request.getHeader("Content-Length") != null ? 
                request.getHeader("Content-Length") : "0");

        // Client variables
        String remoteAddr = request.getHeader("X-Forwarded-For");
        if (remoteAddr == null) {
            remoteAddr = "127.0.0.1";
        }
        env.put("REMOTE_ADDR", remoteAddr);

        // Script variables
        env.put("SCRIPT_NAME", scriptPath.getFileName().toString());
        env.put("SCRIPT_FILENAME", scriptPath.toAbsolutePath().toString());

        // HTTP headers (prefixed with HTTP_)
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            String headerName = header.getKey().toUpperCase().replace("-", "_");
            env.put("HTTP_" + headerName, header.getValue());
        }

        return env;
    }

    /**
     * Parse CGI output into HTTP response.
     * CGI scripts output headers followed by a blank line, then the body.
     */
    private Response parseCgiOutput(String output) {
        ResponseBuilder builder = new ResponseBuilder();
        boolean hasContentType = false;

        String[] parts = output.split("\r?\n\r?\n", 2);
        if (parts.length < 2) {
            // No headers, treat entire output as body
            return builder.setStatus(200)
                    .setHeader("Content-Type", "text/plain")
                    .setBody(output.getBytes())
                    .build();
        }

        String headerSection = parts[0];
        String body = parts.length > 1 ? parts[1] : "";

        // Parse headers
        String[] headerLines = headerSection.split("\r?\n");
        for (String headerLine : headerLines) {
            int colonPos = headerLine.indexOf(':');
            if (colonPos > 0) {
                String headerName = headerLine.substring(0, colonPos).trim();
                String headerValue = headerLine.substring(colonPos + 1).trim();

                // Handle special CGI headers
                if (headerName.equalsIgnoreCase("Status")) {
                    try {
                        int statusCode = Integer.parseInt(headerValue.split(" ")[0]);
                        builder.setStatus(statusCode);
                    } catch (Exception e) {
                        builder.setStatus(200);
                    }
                } else if (headerName.equalsIgnoreCase("Location")) {
                    builder.setHeader("Location", headerValue);
                } else {
                    builder.setHeader(headerName, headerValue);
                    if (headerName.equalsIgnoreCase("Content-Type")) {
                        hasContentType = true;
                    }
                }
            }
        }

        // Set default content type if not provided
        if (!hasContentType) {
            builder.setHeader("Content-Type", "text/html");
        }

        builder.setBody(body.getBytes());
        return builder.build();
    }

    /**
     * Check if a file extension is configured for CGI.
     */
    public boolean isCgiExtension(String extension) {
        return extension.equalsIgnoreCase("py") || extension.equalsIgnoreCase("cgi");
    }
}

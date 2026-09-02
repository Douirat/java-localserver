package http.cgi;

import config.RouteConfig;
import config.ServerConfig;
import http.request.Request;
import http.response.Response;
import http.response.ResponseBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class CGIHandler {
    private static final long PROCESS_TIMEOUT_SECONDS = 5;

    public static Response execute(Request request, RouteConfig route, ServerConfig server) {
        String relative = request.getPath().substring(route.getPath().length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path root = Path.of(route.getRoot()).toAbsolutePath().normalize();
        Path script = root.resolve(relative).normalize();

        if (!script.startsWith(root) || !Files.isRegularFile(script)) {
            return ResponseBuilder.notFound(server);
        }

        String extension = extension(script.getFileName().toString());
        String interpreter = route.getCgiExtensions().get(extension);
        if (interpreter == null) {
            return ResponseBuilder.notFound(server);
        }

        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(interpreter, script.toAbsolutePath().toString())
                    .directory(root.toFile())
                    .redirectErrorStream(true);
            Map<String, String> environment = builder.environment();
            environment.put("GATEWAY_INTERFACE", "CGI/1.1");
            environment.put("REQUEST_METHOD", request.getMethod());
            environment.put("PATH_INFO", script.toAbsolutePath().toString());
            environment.put("QUERY_STRING", queryString(request));
            environment.put("CONTENT_TYPE", valueOrEmpty(request.getHeader("content-type")));
            environment.put("CONTENT_LENGTH", String.valueOf(request.getBody() == null ? 0 : request.getBody().length));

            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                String name = header.getKey().toUpperCase().replace('-', '_');
                if (!name.equals("CONTENT_TYPE") && !name.equals("CONTENT_LENGTH")) {
                    environment.put("HTTP_" + name, header.getValue());
                }
            }

            process = builder.start();

            byte[] body = request.getBody() == null ? new byte[0] : request.getBody();
            try (OutputStream out = process.getOutputStream()) {
                out.write(body);
            }

            byte[] output = process.getInputStream().readAllBytes();

            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return ResponseBuilder.gatewayTimeout(server);
            }

            if (process.exitValue() != 0) {
                return ResponseBuilder.internalServerError(server);
            }
            return parseResponse(output, server);
        } catch (IOException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return ResponseBuilder.internalServerError(server);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return ResponseBuilder.gatewayTimeout(server);
        }
    }

    private static Response parseResponse(byte[] output, ServerConfig server) {
        String text = new String(output, StandardCharsets.ISO_8859_1);
        int separator = text.indexOf("\r\n\r\n");
        int separatorLength = 4;
        if (separator < 0) {
            separator = text.indexOf("\n\n");
            separatorLength = 2;
        }
        if (separator < 0) {
            return ResponseBuilder.internalServerError(server);
        }

        ResponseBuilder response = ResponseBuilder.create();
        String headerBlock = text.substring(0, separator);
        for (String line : headerBlock.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (name.equalsIgnoreCase("Status")) {
                String[] status = value.split(" ", 2);
                try {
                    int code = Integer.parseInt(status[0]);
                    response.status(code, status.length > 1 ? status[1] : "");
                } catch (NumberFormatException e) {
                    return ResponseBuilder.internalServerError(server);
                }
            } else {
                response.header(name, value);
            }
        }
        byte[] body = text.substring(separator + separatorLength).getBytes(StandardCharsets.ISO_8859_1);
        return response.body(body).build();
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private static String queryString(Request request) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : request.getQueryParameters().entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return query.toString();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}

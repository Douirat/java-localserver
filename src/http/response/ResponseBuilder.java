package http.response;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import config.ServerConfig;

public class ResponseBuilder implements RespondingBuilder {

    private final Response response = new Response();

    private ResponseBuilder() {
    }

    public static ResponseBuilder create() {
        return new ResponseBuilder();
    }

    @Override
    public ResponseBuilder status(int code, String text) {
        response.setStatus(code, text);
        return this;
    }

    @Override
    public ResponseBuilder header(String name, String value) {
        response.addHeader(name, value);
        return this;
    }

    @Override
    public ResponseBuilder headers(Map<String, String> headers) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            response.addHeader(e.getKey(), e.getValue());
        }
        return this;
    }

    @Override
    public ResponseBuilder body(byte[] body) {
        response.setBody(body);
        return this;
    }

    @Override
    public ResponseBuilder body(String body) {
        response.setBody(body);
        return this;
    }

    @Override
    public Response build() {
        return response;
    }

    // file streaming (zero-copy)
    public static Response okFile(String contentType, long size, FileChannel fc) {
        Response r = new Response(200, "OK");
        r.addHeader("Content-Type", contentType);
        r.setFileChannel(fc, size);
        return r;
    }

    // default and custom error pages required by the audit: 400, 403, 404, 405, 413, 500

    public static Response ok(byte[] body, String contentType) {
        return create().status(200, "OK").header("Content-Type", contentType).body(body).build();
    }

    public static Response badRequest() {
        return badRequest(null);
    }

    public static Response badRequest(ServerConfig server) {
        return errorPage(400, "Bad Request", server);
    }

    public static Response forbidden() {
        return forbidden(null);
    }

    public static Response forbidden(ServerConfig server) {
        return errorPage(403, "Forbidden", server);
    }

    public static Response notFound() {
        return notFound(null);
    }

    public static Response notFound(ServerConfig server) {
        return errorPage(404, "Not Found", server);
    }

    public static Response methodNotAllowed(String allowedMethods) {
        return methodNotAllowed(allowedMethods, null);
    }

    public static Response methodNotAllowed(String allowedMethods, ServerConfig server) {
        Response response = errorPage(405, "Method Not Allowed", server);
        response.addHeader("Allow", allowedMethods);
        return response;
    }

    public static Response payloadTooLarge() {
        return payloadTooLarge(null);
    }

    public static Response payloadTooLarge(ServerConfig server) {
        return errorPage(413, "Payload Too Large", server);
    }

    public static Response internalServerError() {
        return internalServerError(null);
    }

    public static Response internalServerError(ServerConfig server) {
        return errorPage(500, "Internal Server Error", server);
    }

    public static Response gatewayTimeout() {
        return gatewayTimeout(null);
    }

    public static Response gatewayTimeout(ServerConfig server) {
        return errorPage(504, "Gateway Timeout", server);
    }

    public static Response redirect(int code, String location) {
        return create()
                .status(code, code == 301 ? "Moved Permanently" : "Found")
                .header("Location", location)
                .body(new byte[0])
                .build();
    }

    public static Response errorPage(int code, String text) {
        return errorPage(code, text, null);
    }

    public static Response errorPage(int code, String text, ServerConfig server) {
        byte[] bodyBytes = null;
        if (server != null && server.getErrorPages() != null) {
            String pagePath = server.getErrorPages().get(code);
            if (pagePath != null) {
                try {
                    Path path = Path.of(pagePath);
                    if (Files.exists(path) && Files.isRegularFile(path)) {
                        bodyBytes = Files.readAllBytes(path);
                    }
                } catch (IOException ignored) {
                }
            }
        }

        if (bodyBytes == null) {
            String html = "<html><body><h1>" + code + " " + text + "</h1></body></html>";
            bodyBytes = html.getBytes(StandardCharsets.UTF_8);
        }

        return create()
                .status(code, text)
                .header("Content-Type", "text/html")
                .body(bodyBytes)
                .build();
    }
}
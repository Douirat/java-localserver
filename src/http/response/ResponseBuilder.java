package http.response;

import java.util.Map;

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

    // ---- default error pages required by the audit: 400, 403, 404, 405, 413, 500 ----

    public static Response ok(byte[] body, String contentType) {
        return create().status(200, "OK").header("Content-Type", contentType).body(body).build();
    }

    public static Response badRequest() {
        return errorPage(400, "Bad Request");
    }

    public static Response forbidden() {
        return errorPage(403, "Forbidden");
    }

    public static Response notFound() {
        return errorPage(404, "Not Found");
    }

    public static Response methodNotAllowed(String allowedMethods) {
        return create()
                .status(405, "Method Not Allowed")
                .header("Content-Type", "text/html")
                .header("Allow", allowedMethods)
                .body("<html><body><h1>405 Method Not Allowed</h1></body></html>")
                .build();
    }

    public static Response payloadTooLarge() {
        return errorPage(413, "Payload Too Large");
    }

    public static Response internalServerError() {
        return errorPage(500, "Internal Server Error");
    }

    public static Response redirect(int code, String location) {
        return create()
                .status(code, code == 301 ? "Moved Permanently" : "Found")
                .header("Location", location)
                .body(new byte[0])
                .build();
    }

    private static Response errorPage(int code, String text) {
        String html = "<html><body><h1>" + code + " " + text + "</h1></body></html>";
        return create()
                .status(code, text)
                .header("Content-Type", "text/html")
                .body(html)
                .build();
    }
}
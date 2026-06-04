package http.handler;

import http.request.Request;
import http.response.Response;

@FunctionalInterface
public interface Handler {
    Response handle(Request request);
}
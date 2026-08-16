package http.router;

import config.ServerConfig;
import http.request.Request;
import http.response.Response;

public interface Routing {
    Response route(Request request, ServerConfig server);
}
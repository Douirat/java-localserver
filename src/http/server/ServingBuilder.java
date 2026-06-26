package http.server;

import http.handler.Handler;

public interface ServingBuilder {
    ServingBuilder port(int port);
    ServingBuilder staticRoutes(String dir);
    ServingBuilder get(String path, Handler handler);
    ServingBuilder post(String path, Handler handler);
    ServingBuilder put(String path, Handler handler);
    ServingBuilder delete(String path, Handler handler);
    ServingBuilder patch(String path, Handler handler);
    Server build();
}
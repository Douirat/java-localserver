package http.server;

import http.router.*;

public interface Serving {
    void setPort(int port);
    int getPort();
    Router getRouter();
    void start();
}
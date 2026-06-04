package http.server;

import http.router.*;

public interface Serving {
    void setPort(int port);
    int getPort();
    Router getRouter();
    // void get(String path, Handler handler);
    // void post(String path, Handler handler);
    // void put(String path, Handler handler);
    // void delete(String path, Handler handler);
    void start();
}
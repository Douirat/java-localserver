package http.server;

public interface Serving {
    void setPort(int port);
    int getPort();
    void start();
}
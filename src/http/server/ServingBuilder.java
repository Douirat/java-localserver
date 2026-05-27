package http.server;

public interface ServingBuilder {
    ServingBuilder port(int port);
    Server build();
}
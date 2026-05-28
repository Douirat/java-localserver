package http.server;

public interface ServingBuilder {
    ServingBuilder port(int port);
    // TODO: Try to add the start().
    Server build();
}
package http;

public interface Handler{
    Response handle(Request request);
}
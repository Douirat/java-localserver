import http.server.*;

public class Main {
    public static void main(String[] args){
        Server server = new ServerBuilder()
                .port(8080)
                .build();

        server.start();
    }
}
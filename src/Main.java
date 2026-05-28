import http.server.*;

public class Main {
    public static void main(String[] args){
        Server server = new ServerBuilder()
                .port(8000)
                .build();

        server.start();
    }
}
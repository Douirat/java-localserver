import server.*;
import http.*;
import router.Router;
import models.User;

public class Main {
    public static void main(String[] args) {

        Router router = new Router();

        router.addRoute("POST", "/api/users", (Request request) -> {

            String data = new String(request.getBody());
            System.out.println("RAW INPUT: " + data);

            // create a test object (simulate DB or logic)
            User user = new User("Ali", 25, "ali@test.com");

            Response response = new Response();
            response.setStatus(200);
            response.setHeader("Content-Type", "application/json");

            // IMPORTANT: this should go through your serializer
            response.setBody(user);

            return response;
        });

        Server server = new Server(8080, router);
        server.listenAndServe();
    }
}
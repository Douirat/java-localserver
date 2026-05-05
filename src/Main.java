import server.*;
import http.*;
import router.Router;
import models.User;
import java.util.*;

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

     router.addRoute("POST", "/api/users/login", (Request request) -> {
        String data = new String(request.getBody());
        System.out.println("RAW INPUT: " + data);

        // create a test object (simulate DB or logic)
        User user = new User("John", 30, "john1@example.com");
        Response response = new Response();
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.setBody(user);
        return response;
     });

    // add a route to test extraction path variables:
    router.addRoute("GET", "/api/posts/{postId}/users/{userId}", (Request request) -> {
        // Access path variables
        String postId = request.getPathVariables().get("postId");
        String userId = request.getPathVariables().get("userId");

        System.out.println("Post ID: " + postId);
        System.out.println("User ID: " + userId);

        // Simulate a response
        Response response = new Response();
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
            Map<String, String> map = Map.of(
                "postId", postId,
                "userId", userId
         );
        response.setBody(map);
        return response;
    });

        Server server = new Server(8080, router);
        server.listenAndServe();
    }
}
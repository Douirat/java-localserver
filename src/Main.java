import server.*;
import http.*;
import router.Router;
import models.User;
import java.util.*;

public class Main {
    public static void main(String[] args) {

        Router router = new Router();

        router.addRoute("GET", "/api/cookies", (Request request) ->{
           Map<String, String> cookies = request.getCookies();
            System.out.println("Received Cookies:");
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                System.out.println(entry.getKey() + " = " + entry.getValue());
            }

            Response response = new Response();
            response.setStatus(200);
            response.setHeader("Content-Type", "application/json");
            response.setBody(Map.of("message", "Cookies received successfully", "cookies", cookies));
            return response;
        });
 
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

    //  add a route to test returning a list of 6 user objects:
    router.addRoute("GET", "/api/users", (Request request) -> {
        List<User> users = Arrays.asList(
            new User("Alice", 28, "alice@example.com"),
            new User("Bob", 35, "bob@example.com"),
            new User("Charlie", 22, "charlie@example.com"),
            new User("David", 40, "david@example.com"),
            new User("Eve", 31, "eve@example.com"),
            new User("Frank", 29, "frank@example.com")
        );
        Response response = new Response();
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.setBody(users);
        return response;
    });

    // add another route to test returning a map of user objects:
    router.addRoute("GET", "/api/users/map", (Request request) -> {
        Map<String, User> userMap = new HashMap<>();
        userMap.put("user1", new User("Alice", 28, "alice@example.com"));
        userMap.put("user2", new User("Bob", 35, "bob@example.com"));
        userMap.put("user3", new User("Charlie", 22, "charlie@example.com"));
        userMap.put("user4", new User("David", 40, "david@example.com"));
        userMap.put("user5", new User("Eve", 31, "eve@example.com"));
        userMap.put("user6", new User("Frank", 29, "frank@example.com"));

        Response response = new Response();
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.setBody(userMap);
        return response;
    });

    // add a route to test map of dates:
    router.addRoute("GET", "/api/dates", (Request request) -> {
        Map<String, Date> dateMap = new HashMap<>();
        dateMap.put("today", new Date());
        dateMap.put("tomorrow", new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));
        dateMap.put("yesterday", new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));

        Response response = new Response();
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.setBody(dateMap);
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
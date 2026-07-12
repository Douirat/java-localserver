import http.server.ServerBuilder;
import http.server.Server;
import http.config.ConfigLoader;
import http.request.Request;
import http.response.Response;
import model.User;
import http.response.cookie.*;
import http.admin.AdminDashboard;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {

        ServerBuilder serverBuilder = new ServerBuilder();

        // Load configuration from config.json if it exists
        try {
            ConfigLoader configLoader = new ConfigLoader("config.json", serverBuilder);
            configLoader.load();
            System.out.println("Configuration loaded from config.json");
        } catch (Exception e) {
            System.out.println("Could not load config.json, using defaults: " + e.getMessage());
        }

        Server server = serverBuilder
            
            .get("/api/cookies", (Request request) -> {
                Map<String, String> cookies = request.getCookies();
                Response response = new Response();
                response.setStatus(200);
                response.setHeader("Content-Type", "application/json");
                response.setBody(cookies);

                Cookie cookie = new Cookie("sessionId", "abc123");
                cookie.setDomain("localhost");
                cookie.setPath("/api");
                cookie.setExpires(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));
                cookie.setHttpOnly(true);
                response.addCookie(cookie);
                return response;
            })

            .post("/api/users", (Request request) -> {
                String data = new String(request.getBody());
                System.out.println("RAW INPUT: " + data);

                User user = new User("Ali", 25, "ali@test.com");
                Response response = new Response();
                response.setStatus(200);
                response.setHeader("Content-Type", "application/json");
                response.setBody(user);
                return response;
            })

            .post("/api/users/login", (Request request) -> {
                String data = new String(request.getBody());
                System.out.println("RAW INPUT: " + data);

                User user = new User("John", 30, "john1@example.com");
                Response response = new Response();
                response.setStatus(200);
                response.setHeader("Content-Type", "application/json");
                response.setBody(user);
                return response;
            })

            .get("/api/users", (Request request) -> {
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
            })

            .get("/api/users/map", (Request request) -> {
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
            })

            .get("/api/dates", (Request request) -> {
                Map<String, Date> dateMap = new HashMap<>();
                dateMap.put("today", new Date());
                dateMap.put("tomorrow", new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));
                dateMap.put("yesterday", new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));

                Response response = new Response();
                response.setStatus(200);
                response.setHeader("Content-Type", "application/json");
                response.setBody(dateMap);
                return response;
            })

            .get("/api/posts/{postId}/users/{userId}", (Request request) -> {
                String postId = request.getPathVariables().get("postId");
                String userId = request.getPathVariables().get("userId");

                System.out.println("Post ID: " + postId);
                System.out.println("User ID: " + userId);

                Map<String, String> map = Map.of(
                    "postId", postId,
                    "userId", userId
                );

                Response response = new Response();
                response.setStatus(200);
                response.setHeader("Content-Type", "application/json");
                response.setBody(map);
                return response;
            })

            .delete("/api/users/{userId}", (Request request) -> {
                String userId = request.getPathVariables().get("userId");
                System.out.println("DELETE request for user ID: " + userId);

                Response response = new Response();
                response.setStatus(200);
                response.setHeader("Content-Type", "application/json");
                response.setBody(Map.of("deleted", userId));
                return response;
            })

            .get("/admin", new AdminDashboard())
            .build();

        server.start();
    }
}
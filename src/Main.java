import server.*;
import http.*;
import router.Router;


public class Main{
    public static void main(String[] args){
        Router router = new Router();
        
        router.addRoute("POST", "/api/comments", (Request request)->{
            String data = new String(request.getBody());
            System.out.println(data);
            Response response = new Response();
            response.setVersion("HTTP/1.1");
            response.setStatus(200);
            response.setStatusReason(200);
            response.setHeader("Content-Type", "text/plain");
            response.setBody(data);
            return response;
        });

        Server server = new Server(8080, router);
        server.listenAndServe();
    }
}
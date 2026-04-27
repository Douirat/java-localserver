import server.*;
import types.Request;
import router.Router;


public class Main{
    public static void main(String[] args){
        Router router = new Router();
        
        router.addRoute("POST", "/api/comments", (Request reuest)->{
            System.out.println(reuest.toString());
        });

        Server server = new Server(8080, router);
        server.listenAndServe();
    }
}
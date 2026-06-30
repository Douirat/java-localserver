package http.server;

import http.handler.Handler;
public class ServerBuilder implements ServingBuilder {
    private Server server;

    public ServerBuilder(){
        this.server = new Server();
    }

    @Override
    public ServingBuilder port(int port){
        this.server.setPort(port);
        return this;
    }

    // @Override
    // public ServingBuilder staticRoutes(String dir){
    //     this.server.setStaticDirectory(dir);
    //     return this;
    // }

    // add routes to the router following the building mechanism to ease the API building for the user:
    @Override
    public ServingBuilder get(String path, Handler handler){
        this.server.getRouter().addRoute("GET", path, handler);
        return this;
    }

    @Override
    public ServingBuilder post(String path, Handler handler){
        this.server.getRouter().addRoute("POST", path, handler);
        return this;
    }

    @Override
    public ServingBuilder put(String path, Handler handler){
        this.server.getRouter().addRoute("PUT", path, handler);
        return this;
    }

    @Override
    public ServingBuilder delete(String path, Handler handler){
        this.server.getRouter().addRoute("DELETE", path, handler);
        return this;
    }


    @Override
    public ServingBuilder patch(String path, Handler handler){
        this.server.getRouter().addRoute("PATCH", path, handler);
        return this;
    }

    @Override
    public ServingBuilder serveStatic(String path){
        this.server.getRouter().setStaticDirectory(path);
        return this;
    }

    @Override
    public Server build(){
        return this.server;
    }

    
    
    
}
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

    // Configuration methods for ConfigLoader
    public ServingBuilder setPort(int port) {
        this.server.setPort(port);
        return this;
    }

    public ServingBuilder setRoot(String root) {
        this.server.getRouter().setStaticDirectory(root);
        return this;
    }

    public ServingBuilder setDefaultFile(String defaultFile) {
        this.server.getRouter().setDefaultFile(defaultFile);
        return this;
    }

    public ServingBuilder setMaxBodySize(int maxSize) {
        this.server.getRouter().setMaxBodySize(maxSize);
        return this;
    }

    public ServingBuilder setDirectoryListing(boolean enabled) {
        this.server.getRouter().setDirectoryListing(enabled);
        return this;
    }

    public ServingBuilder setErrorPage(String statusCode, String path) {
        this.server.getRouter().setErrorPage(statusCode, path);
        return this;
    }

    public ServingBuilder addCgiExtension(String extension, String interpreter) {
        this.server.getRouter().addCgiExtension(extension, interpreter);
        return this;
    }

    public ServingBuilder addRedirect(String path, String redirect, int statusCode) {
        this.server.getRouter().addRedirect(path, redirect, statusCode);
        return this;
    }

    @Override
    public Server build(){
        return this.server;
    }

    
    
    
}
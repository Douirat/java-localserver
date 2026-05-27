package http.server;

public class ServerBuilder implements ServingBuilder {
    private Server server;

    public ServerBuilder(){
        this.server = new Server();
    }

    @Override
    public ServerBuilder port(int port){
        this.server.setPort(port);
        return this;
    }

    @Override
    public Server build(){
        return this.server;
    }
    
}
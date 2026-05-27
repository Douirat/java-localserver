package server.http;

public class Server{
    private int port;

    public Server(){
        this.port = 8080;
    }

    /**
     * Creating my setters.
     * @return self to allow chaining.
     */
    public Server port(int port){
         if(port < 1 || port > 65535) {
            return this;
         }
         this.port = port;
         return this;
    }

      /**
     * Creating my getters.
     */
    public int getPort(){
        return this.port;
    }
}
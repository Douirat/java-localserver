package http.server;

public class Server implements Serving{
    private int port;

    public Server(){}

    /**
     * Creating my setters.
     */
    @Override
    public void setPort(int port){
         if(port < 1 || port > 65535) {
           this.port = 8080;
         } else {
            this.port = port;
         }
    }


    /**
     * Creating my getters.
     */
    @Override
    public int getPort(){
     return this.port;
    }

}
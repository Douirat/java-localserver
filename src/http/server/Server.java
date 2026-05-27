package http.server;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class Server implements Serving{
    private int port;

    public Server(){}

    public void start(){
try(var serverSocketChannel = ServerSocketChannel.open(); var selector = Selector.open()) {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(this.port));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); // “Track this server socket inside this selector and wake me up whenever a new TCP connection arrives.”
      /**
       * 
       * selector tracks serverSocketChannel
            ↓
          OS monitors listening socket
                      ↓
          new client arrives
                      ↓
          OS marks socket ACCEPT-ready
                      ↓
          selector.select() wakes up
                      ↓
          key.isAcceptable() becomes true
       */
      while (true) {

      // don't bock when no keys are available.
       if(selector.select() == 0) {
        continue;
       }

       for(var key: selector.selectedKeys()){
        if(key.isAcceptable()){
          // TODO: handle a connection in an acceptable state.
        }
             if(key.isReadable()){
          // TODO: handle a connection in an readable state.
        }
             if(key.isWritable()){
          // TODO: handle a connection in an writable state
        }
       }

       selector.selectedKeys().clear();

      }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

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
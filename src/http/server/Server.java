package http.server;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import http.connecting.Connection;
import http.router.*;


public class Server implements Serving {
  
  private int port;
  private Routing router;

  public Server() {
    this.port = 8080;
    this.router = new Router(); // we will set the router later when we implement it.
  }

  public void start() {
    try (var serverSocketChannel = ServerSocketChannel.open(); var selector = Selector.open()) {
      serverSocketChannel.configureBlocking(false);
      serverSocketChannel.bind(new InetSocketAddress(this.port));
      serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
      /**
       * “Wake me up when the OS says this listening socket can accept a connection
       * without blocking.”
       */

      System.out.println("Server is listening on port: " + this.port);
            /**
             * 
             * selector tracks serverSocketChannel
             * ↓
             * OS monitors listening socket
             * ↓
             * new client arrives
             * ↓
             * OS marks socket ACCEPT-ready
             * ↓
             * selector.select() wakes up
             * ↓
             * key.isAcceptable() becomes true
             */
      while (true) {

        // don't bock when no keys are available.
        if (selector.select() == 0) {
          continue;
        }

        for (var key : selector.selectedKeys()) {

                // check if the key is ready to accept a new connection.
                if (key.isAcceptable()) {
                  ServerSocketChannel channel = (ServerSocketChannel) key.channel();

                  SocketChannel client = channel.accept();
                  client.configureBlocking(false);

                  Connection connection = new Connection(client);

                  client.register(selector, SelectionKey.OP_READ, connection);
                }



                // check if the key is ready for reading.
                if (key.isReadable()) {
                  Connection connection = (Connection) key.attachment();
                  SocketChannel channel = connection.getChannel();

                  int bytes = channel.read(connection.getReadBuffer());
                  connection.ParseRequest();

                  if (bytes == -1) {
                    channel.close();
                    key.cancel();
                    continue;
                  }

                  // if the connection is ready to write and done processing the request, we prepare the response and switch to write mode:
                  

                  key.interestOps(SelectionKey.OP_WRITE);
                }



              // check if the key is ready for writing.

                  if (key.isWritable()) {
                    Connection connection = (Connection) key.attachment();
                    SocketChannel channel = connection.getChannel();

                    ByteBuffer buffer = connection.getWriteBuffer();

                    channel.write(buffer);

                    if (!buffer.hasRemaining()) {
                      buffer.clear();
                      key.interestOps(SelectionKey.OP_READ);
                    }
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
  public void setPort(int port) {
    if (port < 1 || port > 65535) {
      this.port = 8080;
    } else {
      this.port = port;
    }
  }

  /**
   * Creating my getters.
   */
  @Override
  public int getPort() {
    return this.port;
  }

  @Override
  public Router getRouter(){
    return (Router) this.router;
  }
 
}
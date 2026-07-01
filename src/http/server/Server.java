package http.server;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import http.connecting.Connection;
import http.connecting.state.ConnectionState;
import http.connecting.state.RequestState;
import http.router.*;
import http.response.*;
import http.response.responseBody.Body;
import http.response.responseBody.FileBody;

public class Server implements Serving {

  private int port;
  private Routing router;
 

  public Server() {
    this.port = 8080;
    this.router = new Router(); // we will set the router later when we implement it.
  }

  public void start() {
    try (var serverSocketChannel = ServerSocketChannel.open(); var selector = Selector.open()) {
      /**
       * @ var selector = Selector.open()
       * Java:
       * 1. Asks the OS for a selector mechanism
       * Linux → epoll
       * macOS/BSD → kqueue
       * Windows → select / IOCP wrapper
       * 2. Allocates a native structure that can:
       * track file descriptors (sockets)
       * store “interest sets” (READ / WRITE / ACCEPT)
       * return “ready sets”
       */

      serverSocketChannel.configureBlocking(false);
      serverSocketChannel.bind(new InetSocketAddress(this.port));
      /**
       * This is where the OS socket is created and attached to a port.
       * 
       * Internally:
       * 
       * A TCP socket is created
       * It is bound to:
       * IP: 0.0.0.0 (usually all interfaces)
       * Port: 8080 (your value)
       * What this means physically
       * The OS now knows:
       * “Any incoming TCP connection targeting port 8080 should go to this socket.”
       */
      serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

      /**
       * “Wake me up when the OS says this listening socket can accept a connection
       * without blocking.”
       */

      System.out.println("Server is listening on port: " + this.port);

      while (true) {

        // don't bock when no keys are available.
        if (selector.select() == 0) {
          continue;
        }

        for (var key : selector.selectedKeys()) {

          if (key.isAcceptable()) {
            ServerSocketChannel channel = (ServerSocketChannel) key.channel();
            SocketChannel client = channel.accept();
            client.configureBlocking(false);
            Connection connection = new Connection(client);
            client.register(selector, SelectionKey.OP_READ, connection);
            continue; // ← done with this key
          }

          if (key.isReadable()) {
            Connection connection = (Connection) key.attachment();
            SocketChannel channel = connection.getChannel();

            int bytes = channel.read(connection.getBuffer());

            if (bytes == -1) {
              channel.close();
              key.cancel();
              continue;
            }

            connection.ParseRequest();

            if (connection.getRequestState() == RequestState.COMPLETE) {
              Response response = this.router.serve(connection.getRequest());
              if (response != null) {
                
                System.out.println("The returned response: " + response.toString());



                connection.setResponse(response);
                if (response.isStatic()) {
                  connection.setAsStaticResponse();

                  Body body = response.getBody();
                  FileChannel fc = ((FileBody) body).getChannel();

                  connection.setFileChannel(fc);
                  connection.setFileSize(fc.size());
                  connection.setFilePosition(0);

                  System.out.println("File size: " + fc.size());


                  
                  String headers = connection.prepareHeaders((int) fc.size());
                  byte[] headersBytes = headers.getBytes();
                  connection.loadBuffer(headersBytes);

                  connection.setConnectionState(ConnectionState.WRITING_HEADERS);

                } else {
                  connection.prepareResponse();
                }

                connection.setConnectionState(ConnectionState.WRITING_HEADERS);
                key.interestOps(SelectionKey.OP_WRITE);
              }
            }
            continue; // ← don't fall through to isWritable() this iteration
          }

          if (key.isWritable()) {

            Connection connection = (Connection) key.attachment();
            SocketChannel channel = connection.getChannel();

            if (connection.isStaticResponse()) {

              if (connection.getConnectionState() == ConnectionState.WRITING_HEADERS) {
                ByteBuffer buffer = connection.getBuffer();
                channel.write(buffer);

                if (!buffer.hasRemaining()) {
                  connection.setConnectionState(ConnectionState.WRITING_BODY);
                  buffer.clear();
                }

                } else if (connection.getConnectionState() == ConnectionState.WRITING_BODY) {
                  long sent = connection.getFileChannel().transferTo(
                      connection.getFilePosition(),
                      connection.getFileSize()
                          - connection.getFilePosition(),
                      channel);

                  if (sent > 0) {
                    connection.setFilePosition(connection.getFilePosition() + sent);
                  }

                  if (connection.getFilePosition() >= connection.getFileSize()) {

                    connection.getFileChannel().close();

                    connection.setConnectionState(
                        ConnectionState.CLOSED);

                    channel.close();
                    key.cancel();
                  }
              }

            } else {

              ByteBuffer buffer = connection.getBuffer();
              channel.write(buffer);

              if (!buffer.hasRemaining()) {
                connection.setConnectionState(ConnectionState.CLOSED);
                buffer.clear();

                key.interestOps(SelectionKey.OP_READ);
              }

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
  public Router getRouter() {
    return (Router) this.router;
  }

}
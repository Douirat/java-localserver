package http.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

import http.connecting.Connection;
import http.connecting.state.ConnectionState;
import http.connecting.state.RequestState;
import http.router.*;
import http.response.*;
import http.response.responseBody.Body;
import http.response.responseBody.FileBody;

public class Server implements Serving {

  private Set<Integer> ports;
  private Routing router;

  public Server() {
     this.ports = new HashSet<>();
    this.router = new Router(); // we will set the router later when we implement it.

       // default port if no configuration is provided
    this.ports.add(8080);
  }

  /**
   * Check if a port is available (not in use).
   */
  private boolean isPortAvailable(int port) {
    try (ServerSocket socket = new ServerSocket(port)) {
      socket.setReuseAddress(true);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public void start() {
    try (var selector = Selector.open()) {
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



        // Create one listening socket per port
        for (int port : ports) {
            // Check for port conflict before binding
            if (!isPortAvailable(port)) {
                System.err.println("Port " + port + " is already in use. Skipping...");
                continue;
            }

            ServerSocketChannel server = ServerSocketChannel.open();

            server.configureBlocking(false);
            server.bind(new InetSocketAddress(port));
            server.register(selector, SelectionKey.OP_ACCEPT);


            System.out.println("Listening on port: " + port);
        }

        System.out.println("Server is listening on ports: " + ports);

      while (true) {

        // don't bock when no keys are available.
        if (selector.select() == 0) {
          continue;
        }

        var selectedKeys = selector.selectedKeys();
        var iterator = selectedKeys.iterator();

        while (iterator.hasNext()) {
          var key = iterator.next();

          if (key.isAcceptable()) {
            ServerSocketChannel channel = (ServerSocketChannel) key.channel();
            SocketChannel client = channel.accept();
            client.configureBlocking(false);
            Connection connection = new Connection(client);
            client.register(selector, SelectionKey.OP_READ, connection);
            iterator.remove(); // ← done with this key
            continue;
          }

          if (key.isReadable()) {
            Connection connection = (Connection) key.attachment();
            SocketChannel channel = connection.getChannel();

            try {
              // Check for timeout
              if (connection.isTimedOut()) {
                System.err.println("Connection timed out, closing");
                channel.close();
                key.cancel();
                continue;
              }

              int bytes = channel.read(connection.getBuffer());

              if (bytes == -1) {
                channel.close();
                key.cancel();
                continue;
              }

              // Update last activity time on read
              connection.updateLastActivity();

              connection.ParseRequest();

              if (connection.getRequestState() == RequestState.COMPLETE) {
                Response response = this.router.serve(connection.getRequest());
                if (response != null) {

                  System.out.println("The returned response: " + response.toString());

                  System.out.println("request debugging: " + connection.getRequest().toString());
                  response.setVersion(connection.getRequest().getVersion());

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

                    System.out.println("==== HEADERS ====");
                    System.out.print(headers);
                    System.out.println("=================");

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
            } catch (Exception e) {
              System.err.println("Error handling client: " + e.getMessage());
              try {
                channel.close();
              } catch (Exception ex) {
                System.err.println("Error closing channel: " + ex.getMessage());
              }
              key.cancel();
            }
            continue; // ← don't fall through to isWritable() this iteration
          }

          if (key.isWritable()) {

            Connection connection = (Connection) key.attachment();
            SocketChannel channel = connection.getChannel();

            try {
              // Check for timeout
              if (connection.isTimedOut()) {
                System.err.println("Connection timed out during write, closing");
                channel.close();
                key.cancel();
                continue;
              }

              if (connection.isStaticResponse()) {

                if (connection.getConnectionState() == ConnectionState.WRITING_HEADERS) {
                  ByteBuffer buffer = connection.getBuffer();
                  channel.write(buffer);

                  if (!buffer.hasRemaining()) {
                    connection.setConnectionState(ConnectionState.WRITING_BODY);
                    buffer.clear();
                  }

                  // Update activity on write
                  connection.updateLastActivity();

                } else if (connection.getConnectionState() == ConnectionState.WRITING_BODY) {
                  long sent = connection.getFileChannel().transferTo(
                      connection.getFilePosition(),
                      connection.getFileSize()
                          - connection.getFilePosition(),
                      channel);

                  if (sent > 0) {
                    connection.setFilePosition(connection.getFilePosition() + sent);
                    connection.updateLastActivity();
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

              // Update activity on write
              connection.updateLastActivity();
            }

            } catch (Exception e) {
              System.err.println("Error writing to client: " + e.getMessage());
              try {
                channel.close();
              } catch (Exception ex) {
                System.err.println("Error closing channel: " + ex.getMessage());
              }
              key.cancel();
            }

          }

        }

      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Add a port to the server.
   */
  @Override
  public void setPort(int port) {
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("Port must be between 1 and 65535");
    }
    this.ports.add(port);
  }

  /**
   * Get the set of ports the server is listening on.
   */
  @Override
  public int getPort() {
    // Return the first port for backward compatibility
    return this.ports.isEmpty() ? 8080 : this.ports.iterator().next();
  }

  /**
   * Get all ports the server is listening on.
   */
  public Set<Integer> getPorts() {
    return this.ports;
  }

  @Override
  public Router getRouter() {
    return (Router) this.router;
  }

}

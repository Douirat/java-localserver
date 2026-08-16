package http.server;

import config.ServerConfig;
import http.connection.ConnectionState;
import http.request.Request;
import http.response.Response;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Server {

    private final String host;
    private final List<Integer> ports;
    private final ServerConfig config;

    private Selector selector;

    private final List<ServerSocketChannel> serverChannels = new ArrayList<>();

    public Server(ServerConfig config) {
        this.config = config;
        this.host = config.getHost();
        this.ports = new ArrayList<>(config.getPorts());
    }

    public void start() throws IOException {

        selector = Selector.open();

        for (int port : ports) {

            // This channel accepts NEW connections.
            ServerSocketChannel serverChannel = ServerSocketChannel.open();

            serverChannel.configureBlocking(false);

            serverChannel.bind(
                    new InetSocketAddress(host, port));

            /**
             * The server socket itself is registered with the selector.
             * OP_ACCEPT means:
             * "Tell me when a new client wants to connect."
             */
            serverChannel.register(
                    selector,
                    SelectionKey.OP_ACCEPT);

            serverChannels.add(serverChannel);

            System.out.println(
                    "Server listening on " + host + ":" + port);
        }

        run();
    }

    private void run() throws IOException {
        while (true) {

            /**
             * Wait until something happens on one of the registered channels.
             *
             * For example:
             * - Client A sends data -> OP_READ
             * - Client B is ready to write -> OP_WRITE
             * - New client connects -> OP_ACCEPT
             */
            selector.select();

            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {

                SelectionKey key = iterator.next();

                // VERY IMPORTANT:
                // Remove it because we are now processing this event.
                iterator.remove();

                if (!key.isValid()) {
                    continue;
                }

                // NEW CLIENT CONNECTED
                try {
                    if (key.isAcceptable()) {
                        acceptClient(key);
                    } else if (key.isReadable()) {
                        read(key);
                    } else if (key.isWritable()) {
                        write(key);
                    }
                } catch (IOException ex) {
                    // I/O error on this client only -> drop it, keep serving everyone else
                    closeQuietly(key);
                }

            }

            selector.selectedKeys().clear();
        }
    }

    private void acceptClient(SelectionKey key) throws IOException {

        // The key belongs to the ServerSocketChannel,
        // because this key represents OP_ACCEPT.
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

        // Accept the new TCP connection.
        //
        // This creates a NEW SocketChannel representing
        // THIS specific client connection.
        SocketChannel client = serverChannel.accept();

        if (client == null) {
            return;
        }

        // The SocketChannel must be non-blocking.
        client.configureBlocking(false);

        System.out.println(
                "Client connected: " + client.getRemoteAddress());

        // ========================================================
        // CREATE STATE FOR THIS SPECIFIC CLIENT
        // ========================================================

        ConnectionState state = new ConnectionState(client);

        // ========================================================
        // REGISTER THIS CLIENT WITH THE SELECTOR
        // ========================================================

        // We want the selector to notify us when this client
        // has data available to READ.
        //
        // Notice:
        //
        // serverChannel → OP_ACCEPT
        // client → OP_READ
        //
        SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);

        // ========================================================
        // ATTACH THE STATE TO THIS CLIENT'S SELECTION KEY
        // ========================================================

        // Now this SelectionKey knows:
        //
        // "This is Client A"
        //
        // and its state is:
        //
        // ConnectionState A
        //
        clientKey.attach(state);
    }

    private void read(SelectionKey key) throws IOException {
        ConnectionState conn = (ConnectionState) key.attachment();
        SocketChannel client = conn.getClient();
        ByteBuffer buf = conn.getReadBuffer();

        int n = client.read(buf); // exactly one read() call per select wakeup
        if (n == -1) {
            closeQuietly(key);
            return;
        }
        if (n == 0) {
            return;
        }

        conn.updateActivity();

        buf.flip();
        Request request = conn.getParser().parse(buf);
        buf.compact(); // keep any unconsumed bytes for the next read (pipelining)

        if (request == null) {
            return; // request incomplete, wait for more bytes
        }

        conn.setRequest(request);
        Response response = handle(request);
        conn.setResponse(response);
        conn.setWriteBuffer(ByteBuffer.wrap(response.toBytes()));

        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void write(SelectionKey key) throws IOException {
        ConnectionState conn = (ConnectionState) key.attachment();
        SocketChannel client = conn.getClient();
        ByteBuffer buf = conn.getWriteBuffer();

        client.write(buf); // exactly one write() call per select wakeup

        if (buf.hasRemaining()) {
            return; // not fully flushed yet, stay in OP_WRITE
        }

        // response fully sent, reset for the next request on this connection
        conn.getParser().reset();
        conn.getReadBuffer().clear();
        conn.setWriteBuffer(null);
        key.interestOps(SelectionKey.OP_READ);
    }

    /**
     * TODO: replace with real routing — Router.resolveRoute(config,
     * request.getPath()),
     * then static file serving / CGI / redirect based on the matched RouteConfig.
     * Stub keeps the read/write loop testable end-to-end right now.
     */
    private Response handle(Request request) {
        Response response = new Response(200, "OK");
        response.addHeader("Content-Type", "text/plain");
        response.setBody(request.getMethod() + " " + request.getPath() + " received\n");
        return response;
    }

    private void closeQuietly(SelectionKey key) {
        try {
            key.channel().close();
        } catch (IOException ignored) {
        }
        key.cancel();
    }

    public void stop() throws IOException {
        for (ServerSocketChannel channel : serverChannels) {
            channel.close();
        }

        if (selector != null) {
            selector.close();
        }
    }
}
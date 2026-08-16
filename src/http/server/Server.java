package http.server;

import config.ServerConfig;
import http.connection.ConnectionState;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Server {

    private final String host;
    private final List<Integer> ports;

    private Selector selector;

    private final List<ServerSocketChannel> serverChannels = new ArrayList<>();

    public Server(ServerConfig config) {
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

                // NEW CLIENT CONNECTED
                if (key.isAcceptable()) {
                    acceptClient(key);
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

    public void stop() throws IOException {
        for (ServerSocketChannel channel : serverChannels) {
            channel.close();
        }

        if (selector != null) {
            selector.close();
        }
    }
}
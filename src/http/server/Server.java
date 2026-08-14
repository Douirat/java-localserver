package http.server;

import config.ServerConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.List;

public class Server {

    private final String host;
    private final List<Integer> ports;

    private final String webRoot;
    private final String defaultIndex;
    private final long maxBodyBytes;
    private final boolean directoryListing;
    private final boolean defaultServer;

    private Selector selector;
    private final List<ServerSocketChannel> serverChannels = new ArrayList<>();

    public Server(ServerConfig config) {
        this.host = config.getHost();
        this.ports = new ArrayList<>(config.getPorts());

        this.webRoot = config.getWebRoot();
        this.defaultIndex = config.getDefaultIndex();
        this.maxBodyBytes = config.getMaxBodyBytes();
        this.directoryListing = config.isDirectoryListing();
        this.defaultServer = config.isDefaultServer();
    }

    public void start() throws IOException {
        
        selector = Selector.open();

        for (int port : ports) {
            ServerSocketChannel serverChannel = ServerSocketChannel.open();

            serverChannel.configureBlocking(false);

            serverChannel.bind(
                new InetSocketAddress(host, port)
            );

            serverChannel.register(
                selector,
                SelectionKey.OP_ACCEPT
            );

            serverChannels.add(serverChannel);

            System.out.println(
                "Server listening on " + host + ":" + port
            );
        }

        run();
    }

    private void run() throws IOException {
        while (true) {
            selector.select();

            for (SelectionKey key : selector.selectedKeys()) {

                if (key.isAcceptable()) {
                    acceptClient(key);
                }
            }

            selector.selectedKeys().clear();
        }
    }

    private void acceptClient(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel =
                (ServerSocketChannel) key.channel();

        SocketChannel client = serverChannel.accept();

        if (client == null) {
            return;
        }

        client.configureBlocking(false);

        System.out.println(
            "Client connected: " + client.getRemoteAddress()
        );

        client.close();
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
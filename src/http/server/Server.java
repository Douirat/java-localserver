package http.server;

import config.RouteConfig;
import config.ServerConfig;
import exceptions.BadRequestException;
import http.connection.ConnectionState;
import http.request.Request;
import http.response.Response;
import http.response.ResponseBuilder;
import http.router.Router;
import http.router.VirtualHost;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Server {

    private final List<ServerConfig> configurations;
    private final Router router = new Router();

    private Selector selector;

    private final List<ServerSocketChannel> serverChannels = new ArrayList<>();

    private static final long TIMEOUT_MS = 30_000L;

    public Server(List<ServerConfig> configurations) {
        this.configurations = configurations;
    }

    public void start() throws IOException {
        selector = Selector.open();
        bindAll();
        run();
    }

    // one ServerSocketChannel per unique host:port. If two server blocks share
    // an address (virtual hosting), they're grouped onto the SAME channel and
    // both attached, so VirtualHost.resolve() can pick between them per request.
    private void bindAll() throws IOException {
        Map<String, List<ServerConfig>> byAddress = new HashMap<>();

        for (ServerConfig sc : configurations) {
            sc.debug();
            for (int port : sc.getPorts()) {
                String key = sc.getHost() + ":" + port;
                byAddress.computeIfAbsent(key, k -> new ArrayList<>()).add(sc);
            }
        }

        // for (Map.Entry<String, List<ServerConfig>> entry : byAddress.entrySet()) {

        // String address = entry.getKey();
        // List<ServerConfig> configs = entry.getValue();

        // System.out.println("Address: " + address);

        // for (ServerConfig config : configs) {
        // System.out.println("Config: " + config);
        // }
        // }

        for (Map.Entry<String, List<ServerConfig>> entry : byAddress.entrySet()) {

            System.err.println("------------------------");
            System.out.println("the key: " + entry.getKey() + " ---> ");
            for (var sc : entry.getValue()) {
                sc.debug();
            }
            System.err.println("------------------------");

            String addr = entry.getKey();
            int sep = addr.lastIndexOf(':');
            String host = addr.substring(0, sep);
            int port = Integer.parseInt(addr.substring(sep + 1));

            ServerSocketChannel channel = ServerSocketChannel.open();
            channel.configureBlocking(false);
            channel.bind(new InetSocketAddress(host, port));

            SelectionKey key = channel.register(selector, SelectionKey.OP_ACCEPT);
            key.attach(entry.getValue()); // List<ServerConfig> sharing this address

            serverChannels.add(channel);
            System.out.println("Server listening on " + addr);
        }
    }

    private void run() throws IOException {
        while (true) {
            /**
             * Wait until something happens on one of the registered channels (max 1 second).
             * For example:
             * - Client A sends data -> OP_READ
             * - Client B is ready to write -> OP_WRITE
             * - New client connects -> OP_ACCEPT
             */
            selector.select(1000);

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
                } catch (Exception ex) {
                    // Unexpected runtime error (e.g. BadRequestException bubbled up
                    // from somewhere other than read()) -> close client, keep running.
                    System.err.println("[server] Unexpected error, closing client: " + ex.getMessage());
                    closeQuietly(key);
                }
            }

            // Check for timed out / hanging connections
            checkTimeouts();
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

        @SuppressWarnings("unchecked")
        List<ServerConfig> candidates = (List<ServerConfig>) key.attachment();

        ConnectionState state = new ConnectionState(client, candidates);

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
        Request request;
        try {
            request = conn.getParser().parse(buf);
        } catch (BadRequestException ex) {
            // Malformed request — send 400 and close; server keeps running.
            System.err.println("[parser] Bad request from client: " + ex.getMessage());
            buf.clear(); // discard poisoned bytes
            conn.getParser().reset();
            ServerConfig server = null;
            if (conn.getCandidates() != null && !conn.getCandidates().isEmpty()) {
                server = VirtualHost.resolve(conn.getCandidates(), null);
            }
            Response bad = ResponseBuilder.badRequest(server);
            conn.setResponse(bad);
            conn.setWriteBuffer(ByteBuffer.wrap(bad.toBytes()));
            key.interestOps(SelectionKey.OP_WRITE);
            return;
        } catch (exceptions.PayloadTooLargeException ex) {
            // Body exceeds client_max_body_size — send 413
            System.err.println("[parser] Payload too large from client: " + ex.getMessage());
            buf.clear(); // discard poisoned bytes
            conn.getParser().reset();
            ServerConfig server = null;
            if (conn.getCandidates() != null && !conn.getCandidates().isEmpty()) {
                server = VirtualHost.resolve(conn.getCandidates(), conn.getParser().getHeaders().get("host"));
            }
            Response tooLarge = ResponseBuilder.payloadTooLarge(server);
            conn.setResponse(tooLarge);
            conn.setWriteBuffer(ByteBuffer.wrap(tooLarge.toBytes()));
            key.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        buf.compact(); // keep any unconsumed bytes for the next read (pipelining)

        if (request == null) {
            return; // request incomplete, wait for more bytes
        }

        conn.setRequest(request);

        // virtual host resolution happens HERE, now that Host header is known
        ServerConfig server = VirtualHost.resolve(conn.getCandidates(), request.getHeader("host"));

        Response response = router.route(request, server);

        response.debug();
        conn.setResponse(response);

        if (response.isStreaming()) {
            // send headers first, then stream file in write()
            conn.setWriteBuffer(ByteBuffer.wrap(response.headersToBytes()));
            conn.setFilePosition(0);
        } else {
            conn.setWriteBuffer(ByteBuffer.wrap(response.toBytes()));
        }

        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void write(SelectionKey key) throws IOException {
        ConnectionState conn = (ConnectionState) key.attachment();
        SocketChannel client = conn.getClient();

        conn.updateActivity();

        // 1. flush header buffer (or full body buffer for non-streaming responses)
        ByteBuffer buf = conn.getWriteBuffer();
        if (buf != null && buf.hasRemaining()) {
            client.write(buf);
            if (buf.hasRemaining())
                return; // not done yet
            conn.setWriteBuffer(null);
        }

        // 2. stream file if this is a streaming response
        Response response = conn.getResponse();
        if (response.isStreaming()) {
            FileChannel fc = response.getFileChannel();
            long remaining = response.getContentLength() - conn.getFilePosition();
            if (remaining > 0) {
                long written = fc.transferTo(conn.getFilePosition(), remaining, client);
                conn.setFilePosition(conn.getFilePosition() + written);
                if (conn.getFilePosition() < response.getContentLength()) {
                    return; // more chunks to send
                }
            }
            response.closeFileChannel();
        }

        // Check if connection should be closed
        Request req = conn.getRequest();
        String connHeader = (req != null) ? req.getHeader("connection") : null;
        if (connHeader != null && "close".equalsIgnoreCase(connHeader.trim())) {
            closeQuietly(key);
            return;
        }

        // 3. fully sent — reset for next request
        conn.updateActivity();
        conn.setRequest(null);
        conn.setResponse(null);
        conn.getParser().reset();
        conn.getReadBuffer().clear();
        conn.setWriteBuffer(null);
        conn.setFilePosition(0);
        key.interestOps(SelectionKey.OP_READ);
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.attachment() instanceof ConnectionState conn) {
                if (now - conn.getLastActivity() > TIMEOUT_MS) {
                    System.out.println("[server] Connection timeout, closing client: " + conn.getClient());
                    closeQuietly(key);
                }
            }
        }
    }

    private void closeQuietly(SelectionKey key) {
        try {
            if (key.attachment() instanceof ConnectionState conn) {
                if (conn.getResponse() != null) {
                    conn.getResponse().closeFileChannel();
                }
            }
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
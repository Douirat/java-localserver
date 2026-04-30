package server;

import http.*;
import java.io.*;
import java.net.*;
import router.*;

/**
 * Minimal TCP server in Java (Hello server)

 *  *This server:

 *  *listens on port 8080 accepts clients reads what they send replies "hello"
 */
// TODO: Need to study the head and option methods:

public class Server {

    private int port;
    private Router router;

    // All args constructor.
    public Server(int port, Router router) {
        this.port = port;
        this.router = router;
    }

    public void listenAndServe() {
        try {
            ServerSocket server = new ServerSocket(this.port);
            System.out.println("Server listening on port " + this.port);
            while (true) {
                // Step 4 + 5: accept()
                Socket client = server.accept();
                this.handleConnection(client);

   
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // create a connection handler:
    private void handleConnection(Socket client) {
        Request request = this.requestParser(client);
        // server the request:
        Response response = this.router.serve(request);
        response.setVersion(request.getVersion());
        // TODO: write the response back to the client: maybe a response writer.
        System.out.println(response.toString());
    }

    private Request requestParser(Socket client) {
        Request request = new Request();
        System.out.println("Client connected");

        try {
            InputStream in = client.getInputStream();
            // BufferedWriter out = new BufferedWriter(
            // new OutputStreamWriter(client.getOutputStream()));

            // Extract and parse the first line:
            String line = readLine(in);
            String[] requestLine = line.split(" ");

            request.setRequestLine(requestLine);

            // Extract ans parse the headers:
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                String[] header = line.split(":");
                String key = header[0].trim();
                String value = header.length > 1 ? header[1].trim() : "";
                request.addHeader(key, value);
            }

            String lenHeader = request.getHeader("Content-Length");

            if (lenHeader != null) {
                int size = Integer.parseInt(lenHeader);
                InputStream inputStream = client.getInputStream();
                System.out.println("the length check: " + size);
                byte[] body = readBody(inputStream, size);
                request.setBody(body);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return request;
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int prev = -1;
        int curr;

        while ((curr = in.read()) != -1) {
            if (prev == '\r' && curr == '\n') {
                break;
            }
            if (prev != -1) {
                buffer.write(prev);
            }
            prev = curr;
        }

        return buffer.toString();
    }

    private byte[] readBody(InputStream in, int contentLength) throws IOException {
        byte[] body = new byte[contentLength];
        int totalRead = 0;

        while (totalRead < contentLength) {
            int bytesRead = in.read(body, totalRead, contentLength - totalRead);

            if (bytesRead == -1) {
                throw new IOException("Client closed connection before sending full body");
            }

            totalRead += bytesRead;
        }

        return body;
    }

}

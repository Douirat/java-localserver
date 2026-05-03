package server;

import http.*;
import java.io.*;
import java.net.*;
import router.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.lang.reflect.Field;


/**
 * Minimal TCP server in Java (Hello server)

 *  *This server:

 *  *listens on port 8080 accepts clients reads what they send replies "hello"
 */
// TODO: Need to study the head and option methods:

public class Server {

    private int port;
    private Router router;

    private String origin = "*";
    private String allowedMethods = "GET, POST, PUT, DELETE, PATCH, OPTIONS";
    private String allowedHeaders = "Content-Type, Authorization";


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
        System.out.println("Received request: "+ request.toString());
        if(request.getMethod().equalsIgnoreCase("OPTIONS")){
            System.out.println("CORS");
            Response res = new Response();
            res.setVersion(request.getVersion());
            res.setStatus(204);
            if(request.getHeader("Origin") != null) {
                res.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
            }else {
                res.setHeader("Access-Control-Allow-Origin", this.origin);
            }
            res.setHeader("Access-Control-Allow-Methods", this.allowedMethods);
            res.setHeader("Access-Control-Allow-Headers", this.allowedHeaders);

            System.out.println("Sending CORS preflight response: " + res.toString());

            try {
                writeResponse(client, res);
            } catch (IOException e) {
                System.err.println("Failed to write response to client");
            }
            finally {
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return;
        }
        // server the request:
        Response response = this.router.serve(request);
        response.setVersion(request.getVersion());
        // Auto-set Content-Type if not already set
            if (response.getHeader("Content-Type") == null) {
                response.setHeader("Content-Type", "application/json");
            }

        // TODO: write the response back to the client: maybe a response writer.
            try {
            writeResponse(client, response);
            } catch (IOException e) {
            System.err.println("Failed to write response to client");
            }
            finally {
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
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
            int idx = line.indexOf(':');
                if (idx == -1) continue; // invalid header line

                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
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

    private void writeResponse(Socket client, Response response) throws IOException {
        
            OutputStream out = client.getOutputStream();
        
        // 1. Serialize first — this may set headers (e.g. Content-Type)
        byte[] bodyBytes = serializeResponse(response);

        // 2. Now Content-Length is also known
        if (bodyBytes.length > 0) {
            response.setHeader("Content-Length", String.valueOf(bodyBytes.length));
        }

        // 3. Write status line
        String statusLine = response.getVersion() + " " + response.getStatus() + " " + response.getStatusReason() + "\r\n";
        out.write(statusLine.getBytes(StandardCharsets.UTF_8));

        // 4. Write headers — now complete
        for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {
            String headerLine = header.getKey() + ": " + header.getValue() + "\r\n";
            out.write(headerLine.getBytes(StandardCharsets.UTF_8));
        }

        // 5. Empty line + body
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        if(bodyBytes.length > 0) {
            out.write(bodyBytes);
        }
    }

    // create a method to serialize the reponse to bytes:
    private byte[] serializeResponse(Response response) {
        Object body = response.getBody();
        if (body == null) return new byte[0];
        if (body instanceof byte[]) return (byte[]) body;
        if (body instanceof String) return ((String) body).getBytes(StandardCharsets.UTF_8);

        try {

            return simpleJson(body).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response body", e);
        }
    }

private String simpleJson(Object obj) {

    StringBuilder sb = new StringBuilder();
    sb.append("{");

    Field[] fields = obj.getClass().getDeclaredFields();

    for (int i = 0; i < fields.length; i++) {
        try {
            fields[i].setAccessible(true);
            Object value = fields[i].get(obj);

            sb.append("\"")
              .append(fields[i].getName())
              .append("\":\"")
              .append(value)
              .append("\"");

            if (i < fields.length - 1) {
                sb.append(",");
            }

        } catch (Exception e) {
            // ignore field
        }
    }

    sb.append("}");
    return sb.toString();
}
}

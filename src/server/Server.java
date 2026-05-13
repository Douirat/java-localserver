package server;

import http.*;
import json.Serializer;

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

    private String defaultOrigin = "*";
    private String defaultAllowedMethods = "GET, POST, PUT, DELETE, PATCH, OPTIONS";
    private String defaultAllowedHeaders = "Content-Type, Authorization";

    private String origin;
    private String allowedMethods;
    private String allowedHeaders;

    private Serializer serializer = new Serializer();


    // All args constructor.
    public Server(int port, Router router) {
        if(port < 1 || port > 65535) {
            throw new exceptions.ServerException("Invalid port number: " + port);
        }
        this.port = port;
        this.router = router;
        this.origin = this.defaultOrigin;
        this.allowedMethods = this.defaultAllowedMethods;
        this.allowedHeaders = this.defaultAllowedHeaders;
    }

    // setters for CORS configuration:
    public void setOrigin(String origin){
        this.origin = origin;
    }

    public void setAllowedMethods(String allowedMethods){
        this.allowedMethods = allowedMethods;
    }

    public void setAllowedHeaders(String allowedHeaders){
        this.allowedHeaders = allowedHeaders;
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

        // Step 6: read the request, parse it, and create a Request object:
        Request request = this.requestParser(client);
        System.out.println("Received request: " + request.toString());


        String responseOrigin = this.origin.equals("*") ? request.getHeader("Origin") : this.origin;
        // Handle CORS preflight request:
        if(request.getMethod().equalsIgnoreCase("OPTIONS")){
            
            Response res = new Response();
            res.setVersion(request.getVersion());
            res.setStatus(204);


            res.setHeader("Access-Control-Allow-Origin", responseOrigin);
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
                      System.err.println("Issue closing client's connection: " + e.getMessage());
                }
            }
            return;
        }

        // server the request:
        Response response = this.router.serve(request);
        response.setVersion(request.getVersion());

                    // add the CORS headers to every response:
                if (request.getHeader("Origin") != null) {
                    response.setHeader("Access-Control-Allow-Origin", responseOrigin);
                } else {
                    response.setHeader("Access-Control-Allow-Origin", this.origin);
                }

        // Auto-set Content-Type if not already set
            if (response.getHeader("Content-Type") == null) {
                response.setHeader("Content-Type", "application/json");
            }

        // TODO: write the response back to the client: maybe a response writer.
            try {
            writeResponse(client, response);
            } catch (IOException e) {
            System.err.println("Failed to write response to client: " + e.getMessage());
            }
            finally {
                try {
                    client.close();
                } catch (IOException e) {
                        System.err.println("Issue closing client's connection: " + e.getMessage());
                }
            }
    }

    private Request requestParser(Socket client) {
        Request request = new Request();
        System.out.println("Client connected");

        try {
            InputStream in = client.getInputStream();
           

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

                if(key.equalsIgnoreCase("Cookie")){
             
                    String[] cookies = value.split(";");
                    for(String cookie : cookies){
                        String[] parts = cookie.split("=");
                        if(parts.length == 2){
                            String cookieName = parts[0].trim();
                            String cookieValue = parts[1].trim();
                            request.addCookie(cookieName, cookieValue);
                        }
                    }
                }else {
                    request.addHeader(key, value);
                }
                
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
            System.err.println("Failed to read request from client: " + e.getMessage());
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
            System.out.println("writing respose: " + response.toString());
            OutputStream out = client.getOutputStream();

        // 1. Serialize first — this may set headers (e.g. Content-Type)
        byte[] bodyBytes = serializer.serializeBody(response.getBody());

        // 2. Now Content-Length is also known.
        if (bodyBytes.length > 0) {
            response.setHeader("Content-Length", String.valueOf(bodyBytes.length));
        }

        // 3. Write status line
        String statusLine = response.getVersion() + " " + response.getStatus() + " " + response.getStatusReason() + "\r\n";
        out.write(statusLine.getBytes(StandardCharsets.UTF_8));

        // 4. Write headers — now complete.
        for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {
            String headerLine = header.getKey() + ": " + header.getValue() + "\r\n";
            out.write(headerLine.getBytes(StandardCharsets.UTF_8));
        }

        if(response.getCookies() != null && !response.getCookies().isEmpty()) {
            for(Cookie cookie : response.getCookies()) {
                StringBuilder cookieHeader = new StringBuilder();
                cookieHeader.append(cookie.getName()).append("=").append(cookie.getValue());

                if (cookie.getDomain() != null) {
                    cookieHeader.append("; Domain=").append(cookie.getDomain());
                }
                if (cookie.getPath() != null) {
                    cookieHeader.append("; Path=").append(cookie.getPath());
                }
                if (cookie.getExpires() != null) {
                    cookieHeader.append("; Expires=").append(serializer.dateToIso8601(cookie.getExpires()));
                }
                if (cookie.isSecure()) {
                    cookieHeader.append("; Secure");
                }
                if (cookie.isHttpOnly()) {
                    cookieHeader.append("; HttpOnly");
                }
                if (cookie.getSameSite() != null) {
                    cookieHeader.append("; SameSite=").append(cookie.getSameSite());
                }

                out.write(("Set-Cookie: " + cookieHeader.toString() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
        }

// handle file streaming if the body is a file descriptor:
        if(response.getBody() instanceof File){
            try {
                out.write("\r\n".getBytes(StandardCharsets.UTF_8)); // end of headers
                this.streamFile(out, (File) response.getBody());
            } catch (IOException e) {
                System.err.println("Failed to stream file: " + e.getMessage());
            }
             return;
        }
        

        // 5. Empty line + body
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        if(bodyBytes.length > 0) {
            out.write(bodyBytes);
        }
    }

    // in case the body is a file descriptor (video, image, etc.) we need to handle it diffrently:
    private void streamFile(OutputStream out, File file) throws IOException{
        try{
            FileInputStream fileIn = new FileInputStream(file);
            byte[] buffer = new byte[64 * 12024]; // 64KB buffer
            int bytesRead;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            fileIn.close();
        } catch (IOException e){
            throw new IOException("Failed to stream file: " + e.getMessage());
        }
    }

}


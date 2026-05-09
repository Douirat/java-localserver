package server;

import http.*;
import java.io.*;
import java.net.*;
import router.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.lang.reflect.Field;
import exceptions.*;
import java.time.temporal.*;
// import the instant class for date/time handling:
import java.time.*;

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
        byte[] bodyBytes = serializeBody(response.getBody());

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

    /**
    * create a method to serialize the reponse to bytes:
    * serialize(Object body):
    * 1. null
    * 2. String / Character
    * 3. Number
    * 4. Boolean
    * 5. Enum
    * 6. Date/Time / UUID / URL / File (string-like objects)
    * 7. Optional
    * 8. Map
    * 9. Array (primitive + object)
    * 10. Collection
    * 11. Fallback → custom object (reflection)
     */
    private byte[] serializeBody(Object body) {

   
        if (body == null) return new byte[0];
        if (body instanceof byte[]) return (byte[]) body;

        // Primitives & String
        if (body instanceof String)    return ((String) body).getBytes(StandardCharsets.UTF_8);
        if (body instanceof Character) return body.toString().getBytes(StandardCharsets.UTF_8);
        if (body instanceof Number || body instanceof Boolean)
            return body.toString().getBytes(StandardCharsets.UTF_8);

    
        // Enum
        if (body instanceof Enum) return body.toString().getBytes(StandardCharsets.UTF_8);


        // String-like types
        if ( body instanceof UUID
        || body instanceof URL
        || body instanceof File)
            return body.toString().getBytes(StandardCharsets.UTF_8);

        
        // Modern java.time types
        if (body instanceof Date) {
        return ("\"" + ((Date) body).toInstant().toString() + "\"").getBytes(StandardCharsets.UTF_8);
        }

        // Optional — unwrap and recurse
        if (body instanceof Optional) {
            Optional<?> opt = (Optional<?>) body;
            return opt.isPresent()
                ? serializeBody(opt.get())
                : "null".getBytes(StandardCharsets.UTF_8);
        }

        if (body instanceof Map)
            return MapToJson((Map<?, ?>) body).getBytes(StandardCharsets.UTF_8);

        // for the primitive array;
        if(body.getClass().isArray() && body.getClass().getComponentType().isPrimitive()) {
            return ListToJson(boxPrimitiveArray(body)).getBytes(StandardCharsets.UTF_8);
        }

        // handle object array:
        if(body.getClass().isArray()) {
            return ListToJson(Arrays.asList((Object[]) body)).getBytes(StandardCharsets.UTF_8);
        }


        if (body instanceof Object[])
            return ListToJson(Arrays.asList((Object[]) body)).getBytes(StandardCharsets.UTF_8);

        if (body instanceof Collection)
            return ListToJson(new ArrayList<>((Collection<?>) body)).getBytes(StandardCharsets.UTF_8); // 

    // Fallback: treat as arbitrary object
    try {
        return objectToJson(body).getBytes(StandardCharsets.UTF_8);
    } catch (Exception e) {
        throw new RuntimeException("Failed to serialize response body: "
            + body.getClass().getName(), e);
    }
    }

    private String ListToJson(List<?> list){
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for(int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof String || item instanceof Number || item instanceof Boolean) {
                sb.append("\"").append(item.toString()).append("\"");
            } else if (item instanceof Map) {
                sb.append(MapToJson((Map<?, ?>) item));
            } else if (item instanceof List) {
                sb.append(ListToJson((List<?>) item));
            } else {
                sb.append(objectToJson(item));
            }

            if (i < list.size() - 1) {
                sb.append(",");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    private String MapToJson(Map<?,?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        int count = 0;
        for (Map.Entry<?,?> entry : map.entrySet()) {
            sb.append("\"")
            .append(entry.getKey().toString())
            .append("\":\"");

            // what if the value is an other map or an object?

        // first handle primitve cases:
        if(entry.getValue() instanceof String || entry.getValue() instanceof Number || entry.getValue() instanceof Boolean) {
            sb.append(entry.getValue().toString());
        }
        //  if the nested value is a map, we recurse on it:
        else if(entry.getValue() instanceof Map){
            sb.append(MapToJson((Map<?, ?>) entry.getValue()));
        }
        // if the nested value is a list, we recurse on it:
        else if(entry.getValue() instanceof Collection){
            sb.append(ListToJson(new ArrayList<>((Collection<?>) entry.getValue())));
        } else if(entry.getValue() != null && entry.getValue().getClass().isArray()){
            sb.append(ListToJson(boxPrimitiveArray(entry.getValue())));
        }else if (entry.getValue() instanceof Date){  sb.append(dateToIso8601((Date) entry.getValue()));}
        // if the nested value is an object, we serialize it:
        else{
            sb.append(objectToJson(entry.getValue()));
         }

            sb.append("\"");

            if(count < map.size() - 1) {
                sb.append(",");
            }
            count++;
        }
        sb.append("}");
        return sb.toString();
    }


    private String objectToJson(Object obj) {

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

    // box primitive arrays to lists for easier JSON serialization:
    private List<Object> boxPrimitiveArray(Object array) {
        int length = java.lang.reflect.Array.getLength(array);
        List<Object> list = new ArrayList<>(length);

        for (int i = 0; i < length; i++) {
            list.add(java.lang.reflect.Array.get(array, i));
        }

        return list;
    }

    // serialize a date to ISO 8601 format:
    private String dateToIso8601(Date date) {
        return date.toInstant().toString();
    }
}


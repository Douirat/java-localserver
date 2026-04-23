package server;

import java.io.*;
import java.net.*;
import java.util.*;
import types.Request;
/**
 * Minimal TCP server in Java (Hello server)

This server:

listens on port 8080
accepts clients
reads what they send
replies "hello"
 */

public class Server{
    private int port;

    // All args constructor.
    public Server(int port){
        this.port = port;
    }

    public void listenAndServe(){
            try {
            ServerSocket server = new ServerSocket(this.port);
            System.out.println("Server listening on port " + this.port);
                while(true){
                // Step 4 + 5: accept()
                Socket client = server.accept();

                Request request = new Request(client);

                System.out.println("Client connected");

                // Streams to communicate with client
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(client.getInputStream()));
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(client.getOutputStream()));

        
               // Extract and parse the first line:
                String line = in.readLine();
                String[] requestLine = line.split(" ");

                request.setRequestLine(requestLine);


                // Extract ans parse the headers:
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    String[] header = line.split(":");
                    String key = header[0];
                    String value = header.length > 1 ? header[1] : "";
                    request.addHeader(key, value);
                }
        

                // Step 7: write()
                out.write("HTTP/1.1 200 OK\r\n");
                out.write("Content-Type: text/plain\r\n");
                out.write("Content-Length: 5\r\n");
                out.write("\r\n");
                out.write("hello");
                out.flush();
                out.flush();

                System.out.println("request ===>"+ request.toString());

                // Step 8: close()
                client.close();
                System.out.println("Client disconnected");
                }                
            } catch (Exception e) {
                    e.printStackTrace();
            }
    }
}
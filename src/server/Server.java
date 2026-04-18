package server;

import java.io.*;
import java.net.*;
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
                System.out.println("Client connected");

                // Streams to communicate with client
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(client.getInputStream()));
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(client.getOutputStream()));

                // Step 6: read()
                String line = in.readLine();
                System.out.println("Client says: " + line);

                // Step 7: write()
                out.write("HTTP/1.1 200 OK\r\n");
                out.write("Content-Type: text/plain\r\n");
                out.write("Content-Length: 5\r\n");
                out.write("\r\n");
                out.write("hello");
                out.flush();
                out.flush();

                // Step 8: close()
                client.close();
                System.out.println("Client disconnected");
                }                
            } catch (Exception e) {
                    e.printStackTrace();
            }
    }
}
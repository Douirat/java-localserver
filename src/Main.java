import java.io.IOException;
import java.util.List;

import config.ConfigLoader;
import config.ServerConfig;
import http.server.Server;

public class Main {
    public static void main(String[] args) {
        String configFilePath = args[0];
    
        try {
            List<ServerConfig> servers = new ConfigLoader(configFilePath).parse();
            Server server = new Server(servers);
            server.start(); // blocks forever in the select loop

        } catch (IOException e) {
            System.err.println("Failed to read config file: " + e.getMessage());
            System.exit(1);
        } catch (RuntimeException e) {
            // catches ConfigParsingException from ConfigLoader
            System.err.println("Invalid configuration: " + e.getMessage());
            System.exit(1);
        }
    }
}
import java.io.IOException;
import java.util.List;

import config.ConfigLoader;
import config.ServerConfig;
import http.server.Server;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <config_file>");
            System.exit(1);
        }

        String configFilePath = args[0];

        try {
            List<ServerConfig> configurations = new ConfigLoader(configFilePath).parse();
            Server server = new Server(configurations);
            server.start(); // Blocks forever in the single-threaded event loop

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
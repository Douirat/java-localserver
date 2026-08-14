import java.io.IOException;
import java.util.List;

import config.ConfigLoader;
import config.ServerConfig;

public class Main {
    public static void main(String[] args) {

        String configFilePath = args[0];
        ConfigLoader configLoader = new ConfigLoader(configFilePath);

        try {

            // 1. Read configuration file
            List<ServerConfig> configs = configLoader.parse();

            // 2. Show configuration
            System.out.println("Server config from file:");
            for (ServerConfig config : configs) {
                System.out.println("Host: " + config.getHost());
                System.out.println("Ports: " + config.getPorts());
                System.out.println("Server Name: " + config.getServerName());
                System.out.println("Max Body Size: " + config.getMaxBodyBytes());
                System.out.println("Default Server: " + config.isDefaultServer());
                System.out.println("Error Pages: " + config.getErrorPages());
            }
            
            // 3. Create the server using the configuration
            // Server server = new Server(config);

            // 4. Start the server
            // server.start();

        } catch (IOException e) {
                   System.err.println(
                    "Error starting server: "
                    + e.getMessage()
            );

            System.exit(1);
        }
    }
}

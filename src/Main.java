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
                System.out.println("Routes:");
                config.getRoutes().forEach(route -> {
                    System.out.println("  Path: " + route.getPath());
                    System.out.println("  Methods: " + route.getMethods());
                    System.out.println("  Redirect URL: " + route.getRedirectUrl());
                    System.out.println("  Redirect Code: " + route.getRedirectCode());
                    System.out.println("  Root: " + route.getRoot());
                    System.out.println("  Index: " + route.getIndex());
                    System.out.println("  CGI Extensions: " + route.getCgiExtensions());
                    System.out.println("  Directory Listing: " + route.isDirectoryListing());
                    System.out.println("  Upload Dir: " + route.getUploadDir());
                });
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

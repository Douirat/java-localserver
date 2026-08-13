import java.io.IOException;

import config.ConfigLoader;
import config.ServerConfig;

public class Main {
    public static void main(String[] args) {

        String configFilePath = args[0];
        ConfigLoader configLoader = new ConfigLoader(configFilePath);

        try {
            ServerConfig config = configLoader.parse();
            System.out.println("Server config from file:");
            System.out.println("Host: " + config.getHost());
            System.out.println("Ports: " + config.getPorts());
        } catch (IOException e) {
            System.err.println("Error reading config file: " + e.getMessage());
            System.exit(1);
        }
    }
}

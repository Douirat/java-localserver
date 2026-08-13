import java.io.IOException;

import config.ConfigLoader;

public class Main {
    public static void main(String[] args) {
        
        String configFilePath = args[0];
        ConfigLoader configLoader = new ConfigLoader(configFilePath);

        try {
            String configContent = configLoader.read();
            System.out.println("Config Content:");
            System.out.println(configContent);
        } catch (IOException e) {
            System.err.println("Error reading config file: " + e.getMessage());
            System.exit(1);
        }
    }
}

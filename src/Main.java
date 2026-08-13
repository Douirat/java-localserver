import java.io.IOException;
import java.util.List;

import config.ConfigLoader;

public class Main {
    public static void main(String[] args) {

        String configFilePath = args[0];
        ConfigLoader configLoader = new ConfigLoader(configFilePath);

        try {
            List<String> tokens = configLoader.tokenize();
            System.out.println("Tokens from config file:");
            for (String token : tokens) {
                System.out.println(token);
            }
        } catch (IOException e) {
            System.err.println("Error reading config file: " + e.getMessage());
            System.exit(1);
        }
    }
}

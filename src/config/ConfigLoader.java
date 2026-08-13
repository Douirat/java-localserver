package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {
    private final Path configPath;

    public ConfigLoader(String configPath) {
        this.configPath = Path.of(configPath);
    }

    public String read() throws IOException {
        return Files.readString(configPath);
    }
}
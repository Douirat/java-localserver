package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ConfigLoader {
    private final Path configPath;

    public ConfigLoader(String configPath) {
        this.configPath = Path.of(configPath);
    }

    public String read() throws IOException {
        return Files.readString(configPath);
    }

    public List<String> tokenize() throws IOException {
        String content = read();
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (char c : content.toCharArray()) {
            if (Character.isWhitespace(c)) {
                addToken(tokens, current);
            } else if (c == '{' || c == '}' || c == ';') {
                addToken(tokens, current);
                tokens.add(String.valueOf(c));
            } else {
                current.append(c);
            }
        }

        addToken(tokens, current);
        return tokens;
    }

    private void addToken(List<String> tokens, StringBuilder current) {
        if (current.length() > 0) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }
}
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
        boolean comment = false;

        for (char c : content.toCharArray()) {

            if (comment) {
                if (c == '\n') {
                    comment = false;
                }
                continue;
            }

            if (c == '#') {
                addToken(tokens, current);
                comment = true;
            } else if (Character.isWhitespace(c)) {
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

    public List<ServerConfig> parse() throws IOException {
        List<String> tokens = tokenize();
        List<ServerConfig> servers = new ArrayList<>();

        int i = 0;
        while (i < tokens.size()) {
            if (!tokens.get(i).equals("server")) {
                throw new IllegalArgumentException("Expected 'server'");
            }

            i++;

            if (!tokens.get(i).equals("{")) {
                throw new IllegalArgumentException("Expected '{'");
            }

            i++;

            ServerConfig server = new ServerConfig();
            
            while (i < tokens.size() && !tokens.get(i).equals("}")) {
                String directive = tokens.get(i++);

                if (directive.equals("host")) {
                    server.setHost(tokens.get(i++));
                } else if (directive.equals("port")) {
                    server.addPort(Integer.parseInt(tokens.get(i++)));
                } else if (directive.equals("default_server")) {
                    server.setDefaultServer(tokens.get(i++).equals("on"));
                } else if (directive.equals("client_max_body_size")) {
                    server.setMaxBodyBytes(parseSize(tokens.get(i++)));
                } else if (directive.equals("server_name")) {
                    server.setServerName(tokens.get(i++));
                } else if (directive.equals("error_page")) {
                    server.addErrorPage(Integer.parseInt(tokens.get(i++)), tokens.get(i++));
                } else {
                    throw new IllegalArgumentException("Unknown directive: " + directive);
                }

                if (!tokens.get(i).equals(";")) {
                    throw new IllegalArgumentException("Expected ';'");
                }

                i++;
            }

            if (i >= tokens.size()) {
                throw new IllegalArgumentException("Expected '}'");
            }

            i++;
            servers.add(server);
        }

        return servers;
    }

    private long parseSize(String value) {
        value = value.toUpperCase();

        if (value.endsWith("M")) {
            return Long.parseLong(value.substring(0, value.length() - 1)) * 1024 * 1024;
        }

        if (value.endsWith("K")) {
            return Long.parseLong(value.substring(0, value.length() - 1)) * 1024;
        }

        if (value.endsWith("G")) {
            return Long.parseLong(value.substring(0, value.length() - 1)) * 1024 * 1024 * 1024;
        }

        return Long.parseLong(value);
    }
}
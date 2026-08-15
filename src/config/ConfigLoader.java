package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import util.TokenReader;

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
        TokenReader reader = new TokenReader(tokens);

        while (reader.hasMore()) {
            servers.add(parseServerBlock(reader));
        }

        validateNoDuplicatePorts(servers);
        return servers;
    }

    private ServerConfig parseServerBlock(TokenReader reader) {
        reader.expect("server");
        reader.expect("{");

        ServerConfig server = new ServerConfig();

        while (!reader.peek().equals("}")) {
            parseServerDirective(reader, server);
        }

        reader.expect("}");
        applyServerDefaults(server);
        return server;
    }

    private void parseServerDirective(TokenReader reader, ServerConfig server) {
        String directive = reader.next();
        switch (directive) {
            case "host" -> server.setHost(reader.next());
            case "port" -> server.addPort(reader.nextInt());
            case "server_name" -> server.setServerName(reader.next());
            case "default_server" -> server.setDefaultServer(reader.next().equals("on"));
            case "client_max_body_size" -> server.setMaxBodyBytes(parseSize(reader.next()));
            case "error_page" -> server.addErrorPage(reader.nextInt(), reader.next());
            case "location" -> {
                server.addRoute(parseLocationBlock(reader));
                return;
            }
            default -> throw new IllegalArgumentException("Unknown server directive: " + directive);
        }
        reader.expect(";");
    }

    private RouteConfig parseLocationBlock(TokenReader reader) {
        RouteConfig route = new RouteConfig();
        route.setPath(reader.next());
        reader.expect("{");

        while (!reader.peek().equals("}")) {
            parseLocationDirective(reader, route);
        }

        reader.expect("}");
        return route;
    }

    private void parseLocationDirective(TokenReader reader, RouteConfig route) {
        String directive = reader.next();
        switch (directive) {
            case "root" -> route.setRoot(reader.next());
            case "index" -> route.setIndex(reader.next());
            case "upload_dir" -> route.setUploadDir(reader.next());
            case "directory_listing" -> route.setDirectoryListing(reader.next().equals("on"));
            case "cgi"               -> route.addCgiExtension(reader.next(), reader.next());
            case "methods" -> {
                while (!reader.peek().equals(";")) {
                    route.addMethod(reader.next());
                }
            }
            case "return" -> {
                route.setRedirectCode(reader.nextInt());
                if (!reader.peek().equals(";")) {
                    route.setRedirectUrl(reader.next());
                }
            }
            default -> throw new IllegalArgumentException("Unknown location directive: " + directive);
        }
        reader.expect(";");
    }

    private void applyServerDefaults(ServerConfig server) {
        if (server.getHost() == null) {
            server.setHost("0.0.0.0");
        }
        if (server.getPorts().isEmpty()){
            throw new IllegalArgumentException("Server block missing 'port'");
        }
    }

    private void validateNoDuplicatePorts(List<ServerConfig> servers) {
        Set<String> seen = new HashSet<>();
        for (ServerConfig s : servers) {
            for (int port : s.getPorts()) {
                String key = s.getHost() + ":" + port;
                if (!seen.add(key)){
                    throw new IllegalArgumentException("Duplicate host:port: " + key);
                }
            }
        }
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
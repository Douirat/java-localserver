package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import exceptions.ConfigParsingException;
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
            try {
                servers.add(parseServerBlock(reader));
            } catch (ConfigParsingException e) {
                System.err.println("[config error] Skipping invalid server block: " + e.getMessage());
                while (reader.hasMore() && !reader.peek().equals("server")) {
                    reader.next();
                }
            }
        }

        List<ServerConfig> validServers = validateAndFilter(servers);
        if (validServers.isEmpty()) {
            throw new ConfigParsingException("No valid server configurations found to start.");
        }

        return validServers;
    }

    private ServerConfig parseServerBlock(TokenReader reader) {
        reader.expect("server");
        reader.expect("{");

        ServerConfig server = new ServerConfig();

        while (reader.hasMore() && !reader.peek().equals("}")) {
            if (reader.peek().equals("server")) {
                throw new ConfigParsingException("Missing closing brace for server block");
            }
            parseServerDirective(reader, server);
        }

        if (!reader.hasMore()) {
            throw new ConfigParsingException("Unexpected end of config: missing closing brace for server block");
        }

        reader.expect("}");
        applyServerDefaults(server);
        return server;
    }

    private void parseServerDirective(TokenReader reader, ServerConfig server) {
        String directive = reader.next();
        switch (directive) {
            case "host" -> server.setHost(reader.next());
            case "port" -> {
                int port = reader.nextInt();
                if (port < 1 || port > 65535) {
                    throw new ConfigParsingException("Invalid port number: " + port + ". Must be between 1 and 65535.");
                }
                if (server.getPorts().contains(port)) {
                    System.err.println("[config warning] Duplicate port '" + port + "' for host '" + server.getHost() + "', skipping");
                } else {
                    server.addPort(port);
                }
            }
            case "server_name" -> server.setServerName(reader.next());
            case "default_server" -> server.setDefaultServer(reader.next().equals("on"));
            case "client_max_body_size" -> {
                long size = parseSize(reader.next());
                if (size < 0) {
                    throw new ConfigParsingException("client_max_body_size cannot be negative.");
                }
                server.setMaxBodyBytes(size);
            }
            case "error_page" -> {
                int code = reader.nextInt();
                if (code < 100 || code > 599) {
                    throw new ConfigParsingException("Invalid HTTP status code for error_page: " + code);
                }
                server.addErrorPage(code, reader.next());
            }
            case "location" -> {
                RouteConfig route = parseLocationBlock(reader);
                server.addRoute(route);
                return;
            }
            default -> {
                System.err.println("[config warning] Unknown server directive '" + directive + "', skipping");
                while (reader.hasMore() && !reader.peek().equals(";") && !reader.peek().equals("}")) {
                    reader.next();
                }
                if (reader.hasMore() && reader.peek().equals(";")) {
                    reader.next();
                }
                return;
            }
        }
        reader.expect(";");
    }

    private RouteConfig parseLocationBlock(TokenReader reader) {
        RouteConfig route = new RouteConfig();
        route.setPath(reader.next());
        reader.expect("{");

        while (reader.hasMore() && !reader.peek().equals("}")) {
            parseLocationDirective(reader, route);
        }

        reader.expect("}");

        if (route.getRoot() == null && route.getRedirectCode() == 0) {
            throw new ConfigParsingException("Location '" + route.getPath() + "' has no root or return directive");
        }

        return route;
    }

    private void parseLocationDirective(TokenReader reader, RouteConfig route) {
        String directive = reader.next();
        switch (directive) {
            case "root" -> route.setRoot(reader.next());
            case "index" -> route.setIndex(reader.next());
            case "upload_dir" -> route.setUploadDir(reader.next());
            case "directory_listing" -> route.setDirectoryListing(reader.next().equals("on"));
            case "cgi" -> route.addCgiExtension(reader.next(), reader.next());
            case "methods" -> {
                while (reader.hasMore() && !reader.peek().equals(";")) {
                    String method = reader.next().toUpperCase();
                    if (!method.equals("GET") && !method.equals("POST") && !method.equals("DELETE")) {
                        throw new ConfigParsingException(
                                "Unsupported HTTP method: " + method + ". Supported methods are GET, POST, DELETE.");
                    }
                    route.addMethod(method);
                }
            }
            case "return" -> {
                int code = reader.nextInt();
                if (code < 100 || code > 599) {
                    throw new ConfigParsingException("Invalid HTTP status code for return directive: " + code);
                }
                route.setRedirectCode(code);
                if (reader.hasMore() && !reader.peek().equals(";")) {
                    route.setRedirectUrl(reader.next());
                }
            }
            default -> {
                System.err.println("[config warning] Unknown location directive '" + directive + "', skipping");
                while (reader.hasMore() && !reader.peek().equals(";") && !reader.peek().equals("}")) {
                    reader.next();
                }
                if (reader.hasMore() && reader.peek().equals(";")) {
                    reader.next();
                }
                return;
            }
        }
        reader.expect(";");
    }

    private void applyServerDefaults(ServerConfig server) {
        if (server.getHost() == null) {
            server.setHost("0.0.0.0");
        }
        if (server.getPorts().isEmpty()) {
            throw new ConfigParsingException("Server block missing 'port' directive");
        }
    }

    private List<ServerConfig> validateAndFilter(List<ServerConfig> servers) {
        Set<String> seenNames = new HashSet<>();
        Set<String> defaultHostPort = new HashSet<>();

        for (ServerConfig server : servers) {
            Set<Integer> uniquePorts = new HashSet<>(server.getPorts());

            for (int port : uniquePorts) {
                String host = server.getHost();
                String name = server.getServerName();

                String nameKey = host + ":" + port + ":" + (name != null ? name : "");
                if (!seenNames.add(nameKey)) {
                    throw new ConfigParsingException(
                            "Duplicate server_name '" + server.getServerName() + "' for " + host + ":" + port);
                }

                if (server.isDefaultServer()) {
                    String defaultKey = host + ":" + port;
                    if (!defaultHostPort.add(defaultKey)) {
                        throw new ConfigParsingException("Multiple default servers for " + host + ":" + port);
                    }
                }
            }
        }
        return servers;
    }

    private long parseSize(String value) {
        value = value.toUpperCase();
        try {
            if (value.endsWith("M") && value.length() > 1) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 1024 * 1024;
            }
            if (value.endsWith("K") && value.length() > 1) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 1024;
            }
            if (value.endsWith("G") && value.length() > 1) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 1024 * 1024 * 1024;
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ConfigParsingException("Invalid size format for client_max_body_size: " + value, e);
        }
    }
}
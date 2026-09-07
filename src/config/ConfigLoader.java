package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        if (server.getPorts().isEmpty()) {
            throw new ConfigParsingException("Server block missing 'port' directive");
        }
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
                    System.err.println("[config warning] Duplicate port '" + port + "' for host '" + server.getHost()
                            + "', skipping");
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
                try {
                    RouteConfig route = parseLocationBlock(reader);
                    server.addRoute(route);
                } catch (ConfigParsingException e) {
                    System.err.println("[config warning] Skipping invalid location: " + e.getMessage());
                    skipCurrentBlock(reader);
                }

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

    private void skipCurrentBlock(TokenReader reader) {
        int depth = 0;

        while (reader.hasMore()) {
            String token = reader.next();

            if (token.equals("{")) {
                depth++;
            } else if (token.equals("}")) {
                if (depth == 0) {
                    return;
                }
                depth--;
            }
        }
    }

    private RouteConfig parseLocationBlock(TokenReader reader) {
        RouteConfig route = new RouteConfig();
        route.setPath(reader.next());
        reader.expect("{");

        while (reader.hasMore() && !reader.peek().equals("}")) {
            parseLocationDirective(reader, route);
        }

        if (route.getRoot() == null && route.getRedirectCode() == 0) {
            throw new ConfigParsingException("Location '" + route.getPath() + "' has no root or return directive");
        }

        if (route.getMethods().isEmpty() && route.getRedirectCode() == 0) {
            throw new ConfigParsingException("Location '" + route.getPath() + "' has no methods defined");
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
            case "cgi" -> {
                String ext = reader.next();
                String interpreter = reader.next();
                if (!ext.startsWith(".")) {
                    throw new ConfigParsingException("CGI extension must start with '.', got: '" + ext + "'");
                }
                if (interpreter.isBlank()) {
                    throw new ConfigParsingException("CGI interpreter path cannot be empty for extension: " + ext);
                }
                route.addCgiExtension(ext, interpreter);
            }
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

    private List<ServerConfig> validateAndFilter(List<ServerConfig> servers) {
        Set<String> seenNames = new HashSet<>();
        Set<String> defaultHostPort = new HashSet<>();
        List<ServerConfig> validServers = new ArrayList<>();

        for (ServerConfig server : servers) {
            boolean valid = true;
            String host = server.getHost();
            String name = server.getServerName();

            boolean hasServerName = name != null && !name.trim().isEmpty();

            for (int port : server.getPorts()) {
                String hostPortKey = host + ":" + port;

                if (hasServerName) {
                    String nameKey = hostPortKey + ":" + name;

                    if (!seenNames.add(nameKey)) {
                        System.err.println("[config error] Duplicate server_name '" + name + "' for " + hostPortKey);
                        valid = false;
                        break;
                    }
                } else {
                    String unnamedKey = hostPortKey + ":<unnamed>";
                    if (!seenNames.add(unnamedKey)) {
                        System.err.println(
                                "[config error] Duplicate server block without distinct server_name for " + hostPortKey);
                        valid = false;
                        break;
                    }
                }

                if (server.isDefaultServer() && !defaultHostPort.add(hostPortKey)) {
                    System.err.println("[config error] Multiple default servers for " + hostPortKey);
                    valid = false;
                    break;
                }
            }

            // Check error_page files exist on disk
            for (Map.Entry<Integer, String> entry : server.getErrorPages().entrySet()) {
                if (!new java.io.File(entry.getValue()).exists()) {
                    System.err.println("[config error] error_page " + entry.getKey()
                            + " file not found: '" + entry.getValue() + "'");
                    valid = false;
                }
            }

            // Check route root and upload_dir exist on disk
            for (RouteConfig route : server.getRoutes()) {
                if (route.getRoot() != null) {
                    java.io.File rootDir = new java.io.File(route.getRoot());
                    if (!rootDir.exists()) {
                        if (rootDir.mkdirs()) {
                            System.out
                                    .println("[config info] Created missing root directory: '" + route.getRoot() + "'");
                        } else {
                            System.err.println(
                                    "[config error] Could not create root directory: '" + route.getRoot() + "'");
                            valid = false;
                        }
                    }
                }
                if (route.getUploadDir() != null) {
                    java.io.File uploadDir = new java.io.File(route.getUploadDir());
                    if (!uploadDir.exists()) {
                        if (uploadDir.mkdirs()) {
                            System.out.println(
                                    "[config info] Created missing upload_dir: '" + route.getUploadDir() + "'");
                        } else {
                            System.err.println(
                                    "[config error] Could not create upload_dir: '" + route.getUploadDir() + "'");
                            valid = false;
                        }
                    }
                }
            }

            // Warn if no server on this port is marked as default
            for (int port : server.getPorts()) {
                String hostPortKey = host + ":" + port;
                if (!server.isDefaultServer() && !defaultHostPort.contains(hostPortKey)) {
                    System.err.println("[config warning] No default_server defined for " + hostPortKey);
                }
            }

            if (valid) {
                validServers.add(server);
            } else {
                System.err.println("[config error] Skipping server '" + host + "'");
            }
        }
        return validServers;
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
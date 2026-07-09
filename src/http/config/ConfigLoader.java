package http.config;

import http.server.ServerBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads server configuration from a JSON file and configures the ServerBuilder.
 */
public class ConfigLoader {

    private final String configPath;
    private final ServerBuilder serverBuilder;

    public ConfigLoader(String configPath, ServerBuilder serverBuilder) {
        this.configPath = configPath;
        this.serverBuilder = serverBuilder;
    }

    /**
     * Parse the configuration file and apply settings to the ServerBuilder.
     */
    public void load() throws IOException {
        String content = Files.readString(Paths.get(configPath));
        Map<String, Object> config = parseJson(content);

        // Parse servers
        List<Map<String, Object>> servers = (List<Map<String, Object>>) config.get("servers");
        if (servers != null) {
            for (Map<String, Object> serverConfig : servers) {
                configureServer(serverConfig);
            }
        }

        // Parse global settings
        parseGlobalSettings(config);
    }

    @SuppressWarnings("unchecked")
    private void configureServer(Map<String, Object> serverConfig) {
        String host = (String) serverConfig.get("host");
        List<Integer> ports = (List<Integer>) serverConfig.get("ports");
        String root = (String) serverConfig.get("root");
        String defaultFile = (String) serverConfig.getOrDefault("default_file", "index.html");
        Long maxBodySize = (Long) serverConfig.get("max_body_size");
        Boolean directoryListing = (Boolean) serverConfig.getOrDefault("directory_listing", false);
        Map<String, String> errorPages = (Map<String, String>) serverConfig.get("error_pages");
        List<Map<String, Object>> routes = (List<Map<String, Object>>) serverConfig.get("routes");
        Map<String, String> cgiExtensions = (Map<String, String>) serverConfig.get("cgi");

        // Configure ports
        if (ports != null) {
            for (Integer port : ports) {
                serverBuilder.setPort(port);
            }
        }

        // Configure root directory
        if (root != null) {
            serverBuilder.setRoot(root);
        }

        // Configure default file
        if (defaultFile != null) {
            serverBuilder.setDefaultFile(defaultFile);
        }

        // Configure max body size
        if (maxBodySize != null) {
            serverBuilder.setMaxBodySize(maxBodySize.intValue());
        }

        // Configure directory listing
        if (directoryListing != null) {
            serverBuilder.setDirectoryListing(directoryListing);
        }

        // Configure error pages
        if (errorPages != null) {
            for (Map.Entry<String, String> entry : errorPages.entrySet()) {
                serverBuilder.setErrorPage(entry.getKey(), entry.getValue());
            }
        }

        // Configure CGI extensions
        if (cgiExtensions != null) {
            for (Map.Entry<String, String> entry : cgiExtensions.entrySet()) {
                serverBuilder.addCgiExtension(entry.getKey(), entry.getValue());
            }
        }

        // Configure routes
        if (routes != null) {
            for (Map<String, Object> routeConfig : routes) {
                String path = (String) routeConfig.get("path");
                String redirect = (String) routeConfig.get("redirect");
                List<String> methods = (List<String>) routeConfig.get("methods");

                if (redirect != null) {
                    // Redirect route
                    int statusCode = routeConfig.containsKey("status_code")
                        ? ((Long) routeConfig.get("status_code")).intValue()
                        : 301;
                    serverBuilder.addRedirect(path, redirect, statusCode);
                }
                // Note: Regular routes with handlers are added via ServerBuilder's get/post/put/delete/patch methods
                // This config loader only sets up redirects and server configuration
            }
        }
    }

    private void parseGlobalSettings(Map<String, Object> config) {
        // Global settings can be added here if needed
    }

    /**
     * Simple JSON parser for configuration files.
     * This is a basic implementation - for production use, consider using a proper JSON library.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        // Remove whitespace and newlines for simpler parsing
        json = json.replaceAll("\\s+", " ").trim();
        
        if (json.startsWith("{") && json.endsWith("}")) {
            return parseObject(json.substring(1, json.length() - 1));
        }
        
        throw new RuntimeException("Invalid JSON configuration");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(String json) {
        Map<String, Object> map = new HashMap<>();
        int depth = 0;
        int start = 0;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                String pair = json.substring(start, i).trim();
                if (!pair.isEmpty()) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replace("\"", "");
                        Object value = parseValue(kv[1].trim());
                        map.put(key, value);
                    }
                }
                start = i + 1;
            }
        }
        
        // Last pair
        String pair = json.substring(start).trim();
        if (!pair.isEmpty()) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                Object value = parseValue(kv[1].trim());
                map.put(key, value);
            }
        }
        
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Object> parseArray(String json) {
        List<Object> list = new ArrayList<>();
        int depth = 0;
        int start = 0;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                String value = json.substring(start, i).trim();
                if (!value.isEmpty()) {
                    list.add(parseValue(value));
                }
                start = i + 1;
            }
        }
        
        // Last value
        String value = json.substring(start).trim();
        if (!value.isEmpty()) {
            list.add(parseValue(value));
        }
        
        return list;
    }

    @SuppressWarnings("unchecked")
    private Object parseValue(String json) {
        json = json.trim();
        
        if (json.startsWith("\"") && json.endsWith("\"")) {
            return json.substring(1, json.length() - 1);
        } else if (json.startsWith("{") && json.endsWith("}")) {
            return parseObject(json.substring(1, json.length() - 1));
        } else if (json.startsWith("[") && json.endsWith("]")) {
            return parseArray(json.substring(1, json.length() - 1));
        } else if (json.equals("true")) {
            return true;
        } else if (json.equals("false")) {
            return false;
        } else if (json.equals("null")) {
            return null;
        } else {
            // Try to parse as number
            try {
                if (json.contains(".")) {
                    return Double.parseDouble(json);
                } else {
                    return Long.parseLong(json);
                }
            } catch (NumberFormatException e) {
                return json;
            }
        }
    }
}

package config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerConfig {

    private String host = "0.0.0.0";
    private final List<Integer> ports = new ArrayList<>();
    private boolean defaultServer;

    private String serverName;
    private final Map<Integer, String> errorPages = new HashMap<>();
    private long maxBodyBytes = 1024 * 1024L;
    private final List<RouteConfig> routes = new ArrayList<>();

    public String getHost() {
        return host;
    }

    public void setHost(String h) {
        this.host = h;
    }

    public List<Integer> getPorts() {
        return ports;
    }

    public void addPort(int p) {
        this.ports.add(p);
    }

    public boolean isDefaultServer() {
        return defaultServer;
    }

    public void setDefaultServer(boolean b) {
        this.defaultServer = b;
    }

    public long getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(long n) {
        this.maxBodyBytes = n;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String name) {
        this.serverName = name;
    }

    public Map<Integer, String> getErrorPages() {
        return errorPages;
    }

    public void addErrorPage(int code, String path) {
        this.errorPages.put(code, path);
    }

    public List<RouteConfig> getRoutes() {
        return routes;
    }

    public void addRoute(RouteConfig route) {
        this.routes.add(route);
    }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "\n  host='" + host + '\'' +
                ",\n  ports=" + ports +
                ",\n  defaultServer=" + defaultServer +
                ",\n  serverName='" + serverName + '\'' +
                ",\n  errorPages=" + errorPages +
                ",\n  maxBodyBytes=" + maxBodyBytes +
                ",\n  routes=" + routes +
                "\n}";
    }

}

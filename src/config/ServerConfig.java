package config;

import java.util.ArrayList;
import java.util.List;

public class ServerConfig {

    private String host;
    private final List<Integer> ports = new ArrayList<>();
    private boolean defaultServer;

    private String webRoot;
    private String defaultIndex;
    private long maxBodyBytes;
    private boolean directoryListing;

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

    public String getWebRoot() {
        return webRoot;
    }

    public void setWebRoot(String r) {
        this.webRoot = r;
    }

    public String getDefaultIndex() {
        return defaultIndex;
    }

    public void setDefaultIndex(String f) {
        this.defaultIndex = f;
    }

    public long getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(long n) {
        this.maxBodyBytes = n;
    }

    public boolean isDirectoryListing() {
        return directoryListing;
    }

    public void setDirectoryListing(boolean b) {
        this.directoryListing = b;
    }
}

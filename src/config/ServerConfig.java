package config;

public class ServerConfig {

    private String host;
    private int port;
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

    public int getPort() {
        return port;
    }

    public void setPort(int p) {
        this.port = p;
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

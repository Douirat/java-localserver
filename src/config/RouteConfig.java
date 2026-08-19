package config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteConfig {

    private String path;
    private final List<String> methods = new ArrayList<>();
    private String redirectUrl;
    private int redirectCode;
    private String root;
    private String index;
    private final Map<String, String> cgiExtensions = new HashMap<>();
    private boolean directoryListing;
    private String uploadDir;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void addMethod(String method) {
        this.methods.add(method);
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public int getRedirectCode() {
        return redirectCode;
    }

    public void setRedirectCode(int redirectCode) {
        this.redirectCode = redirectCode;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public Map<String, String> getCgiExtensions() {
        return cgiExtensions;
    }

    public void addCgiExtension(String extension, String scriptPath) {
        this.cgiExtensions.put(extension, scriptPath);
    }

    public boolean isDirectoryListing() {
        return directoryListing;
    }

    public void setDirectoryListing(boolean directoryListing) {
        this.directoryListing = directoryListing;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public String toString() {
        return "RouteConfig{" +
                "\n  path='" + path + '\'' +
                ",\n  methods=" + methods +
                ",\n  redirectUrl='" + redirectUrl + '\'' +
                ",\n  redirectCode=" + redirectCode +
                ",\n  root='" + root + '\'' +
                ",\n  index='" + index + '\'' +
                ",\n  cgiExtensions=" + cgiExtensions +
                ",\n  directoryListing=" + directoryListing +
                ",\n  uploadDir='" + uploadDir + '\'' +
                "\n}";
    }

    public void debug(){
        System.out.println("debbuging route:");
        System.out.println(this.toString());
    }
}

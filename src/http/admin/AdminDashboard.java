package http.admin;

import http.handler.Handler;
import http.request.Request;
import http.response.Response;
import http.response.ResponseBuilder;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;

/**
 * Admin dashboard handler that provides server metrics and status information.
 */
public class AdminDashboard implements Handler {

    private final long serverStartTime;

    public AdminDashboard() {
        this.serverStartTime = System.currentTimeMillis();
    }

    @Override
    public Response handle(Request request) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<title>Server Dashboard</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 40px; background-color: #f5f5f5; }\n");
        html.append(".container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        html.append("h1 { color: #333; border-bottom: 2px solid #007bff; padding-bottom: 10px; }\n");
        html.append(".metric { margin: 20px 0; padding: 15px; background: #f8f9fa; border-radius: 5px; }\n");
        html.append(".metric-label { font-weight: bold; color: #555; }\n");
        html.append(".metric-value { font-size: 1.2em; color: #007bff; margin-top: 5px; }\n");
        html.append(".section { margin-top: 30px; }\n");
        html.append(".section h2 { color: #444; font-size: 1.3em; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        html.append("<div class=\"container\">\n");
        html.append("<h1>Server Dashboard</h1>\n");

        // Server Information
        html.append("<div class=\"section\">\n");
        html.append("<h2>Server Information</h2>\n");
        html.append("<div class=\"metric\">\n");
        html.append("<div class=\"metric-label\">Server Uptime</div>\n");
        html.append("<div class=\"metric-value\">").append(formatUptime()).append("</div>\n");
        html.append("</div>\n");
        html.append("<div class=\"metric\">\n");
        html.append("<div class=\"metric-label\">Java Version</div>\n");
        html.append("<div class=\"metric-value\">").append(System.getProperty("java.version")).append("</div>\n");
        html.append("</div>\n");
        html.append("<div class=\"metric\">\n");
        html.append("<div class=\"metric-label\">OS</div>\n");
        html.append("<div class=\"metric-value\">").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("</div>\n");
        html.append("</div>\n");
        html.append("</div>\n");

        // Memory Information
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        long memoryPercent = (usedMemory * 100) / maxMemory;

        html.append("<div class=\"section\">\n");
        html.append("<h2>Memory Usage</h2>\n");
        html.append("<div class=\"metric\">\n");
        html.append("<div class=\"metric-label\">Used Memory</div>\n");
        html.append("<div class=\"metric-value\">").append(formatBytes(usedMemory)).append("</div>\n");
        html.append("</div>\n");
        html.append("<div class=\"metric\">\n");
        html.append("<div class=\"metric-label\">Max Memory</div>\n");
        html.append("<div class=\"metric-value\">").append(formatBytes(maxMemory)).append("</div>\n");
        html.append("</div>\n");
        html.append("<div class=\"metric\">\n");
        html.append("<div class=\"metric-label\">Memory Usage</div>\n");
        html.append("<div class=\"metric-value\">").append(memoryPercent).append("%</div>\n");
        html.append("</div>\n");
        html.append("</div>\n");

        // Thread Information
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();

        html.append("<div class=\"section\">\n");
        html.append("<h2>Thread Information</h2>\n");
        html.append("<div class=\"metric\">\n");
        html.append("<div class=\"metric-label\">Current Threads</div>\n");
        html.append("<div class=\"metric-value\">").append(threadCount).append("</div>\n");
        html.append("</div>\n");
        html.append("<div class=\"metric\">\n");
        html.append("<div class=\"metric-label\">Peak Threads</div>\n");
        html.append("<div class=\"metric-value\">").append(peakThreadCount).append("</div>\n");
        html.append("</div>\n");
        html.append("</div>\n");

        // System Information
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        int availableProcessors = osBean.getAvailableProcessors();
        double systemLoadAverage = osBean.getSystemLoadAverage();

        html.append("<div class=\"section\">\n");
        html.append("<h2>System Information</h2>\n");
        html.append("<div class=\"metric\">\n");
        html.append("<div class=\"metric-label\">Available Processors</div>\n");
        html.append("<div class=\"metric-value\">").append(availableProcessors).append("</div>\n");
        html.append("</div>\n");
        html.append("<div class=\"metric\">\n");
        html.append("<div class=\"metric-label\">System Load Average (1 min)</div>\n");
        html.append("<div class=\"metric-value\">").append(String.format("%.2f", systemLoadAverage)).append("</div>\n");
        html.append("</div>\n");
        html.append("</div>\n");

        html.append("</div>\n");
        html.append("</body>\n</html>");

        return new ResponseBuilder()
                .setStatus(200)
                .setHeader("Content-Type", "text/html; charset=UTF-8")
                .setBody(html.toString().getBytes(StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Format uptime in human-readable format.
     */
    private String formatUptime() {
        long uptimeMillis = System.currentTimeMillis() - serverStartTime;
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d days, %d hours, %d minutes", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d minutes, %d seconds", minutes, seconds % 60);
        } else {
            return String.format("%d seconds", seconds);
        }
    }

    /**
     * Format bytes in human-readable format.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}

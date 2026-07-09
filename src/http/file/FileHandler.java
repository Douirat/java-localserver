package http.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for handling file operations including deletion.
 */
public class FileHandler {

    private final String uploadDirectory;

    public FileHandler(String uploadDirectory) {
        this.uploadDirectory = uploadDirectory;
    }

    /**
     * Delete a file from the upload directory.
     * 
     * @param filename The name of the file to delete
     * @return true if the file was deleted, false otherwise
     */
    public boolean deleteFile(String filename) {
        try {
            Path filePath = Paths.get(uploadDirectory, filename).normalize();
            
            // Security check: ensure the file is within the upload directory
            Path uploadPath = Paths.get(uploadDirectory).toAbsolutePath().normalize();
            if (!filePath.startsWith(uploadPath)) {
                return false; // Attempted path traversal
            }
            
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                Files.delete(filePath);
                return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Check if a file exists in the upload directory.
     */
    public boolean fileExists(String filename) {
        try {
            Path filePath = Paths.get(uploadDirectory, filename).normalize();
            Path uploadPath = Paths.get(uploadDirectory).toAbsolutePath().normalize();
            
            if (!filePath.startsWith(uploadPath)) {
                return false;
            }
            
            return Files.exists(filePath) && Files.isRegularFile(filePath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the upload directory path.
     */
    public String getUploadDirectory() {
        return uploadDirectory;
    }
}

package exceptions;

public class ConfigParsingException extends RuntimeException {
    public ConfigParsingException(String message) {
        super(message);
    }

    public ConfigParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}

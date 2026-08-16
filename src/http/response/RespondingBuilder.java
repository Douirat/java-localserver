package http.response;

import java.util.Map;

public interface RespondingBuilder extends Responding {
    RespondingBuilder status(int code, String text);
    RespondingBuilder header(String name, String value);
    RespondingBuilder headers(Map<String, String> headers);
    RespondingBuilder body(byte[] body);
    RespondingBuilder body(String body);
}
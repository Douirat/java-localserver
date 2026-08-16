package util;

import java.util.List;
import exceptions.ConfigParsingException;

public class TokenReader {
    private final List<String> tokens;
    private int i = 0;

    public TokenReader(List<String> tokens) {
        this.tokens = tokens;
    }

    public String next() {
        if (!hasMore()) {
            throw new ConfigParsingException("Unexpected end of configuration file");
        }
        return tokens.get(i++);
    }

    public String peek() {
        if (!hasMore()) {
            return null;
        }
        return tokens.get(i);
    }

    public String peekAt(int offset) {
        int idx = i + offset;
        if (idx >= tokens.size()) {
            return null;
        }
        return tokens.get(idx);
    }

    public int nextInt() {
        String token = next();
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new ConfigParsingException(
                "Expected a valid integer, but got: '" + token + "'", e);
        }
    }

    public void expect(String expectedToken) {
        if (!hasMore()) {
            throw new ConfigParsingException(
                "Expected '" + expectedToken + "', but reached end of configuration file");
        }
        String actual = next();
        if (!actual.equals(expectedToken)) {
            throw new ConfigParsingException(
                "Expected '" + expectedToken + "', but got '" + actual + "'");
        }
    }

    public int position() {
        return i;
    }

    public void seek(int position) {
        if (position < 0 || position > tokens.size()) {
            throw new IllegalArgumentException("Invalid seek position: " + position);
        }
        this.i = position;
    }

    public boolean hasMore() {
        return i < tokens.size();
    }
}
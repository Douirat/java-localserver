package util;

import java.util.List;

public class TokenReader {
    private final List<String> tokens;
    private int i = 0;

    public TokenReader(List<String> tokens) {
        this.tokens = tokens;
    }

    public String next() {
        if (i >= tokens.size()) {
            throw new IllegalArgumentException("Unexpected end of config");
        }
        return tokens.get(i++);
    }

    public String peek() {
        if (i >= tokens.size()) {
            throw new IllegalArgumentException("Unexpected end of config");
        }
        return tokens.get(i);
    }

    public int nextInt() {
        try {
            return Integer.parseInt(next());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected number, got: " + tokens.get(i - 1));
        }
    }

    public void expect(String token) {
        String actual = next();
        if (!actual.equals(token)) {
            throw new IllegalArgumentException("Expected '" + token + "', got '" + actual + "'");
        }
    }

    public boolean hasMore() {
        return i < tokens.size();
    }
}
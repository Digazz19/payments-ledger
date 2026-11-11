package fraud.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.of(
        "RULE", TokenType.RULE,
        "WHEN", TokenType.WHEN,
        "THEN", TokenType.THEN,
        "AND", TokenType.AND,
        "OR", TokenType.OR,
        "NOT", TokenType.NOT,
        "REVIEW", TokenType.REVIEW,
        "BLOCK", TokenType.BLOCK,
        "velocity", TokenType.VELOCITY
    );

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int current = 0;
    private int line = 1;
    private int column = 1;

    private int start;
    private int startLine;
    private int startColumn;

    private Lexer(String source) {
        this.source = source;
    }

    public static List<Token> tokenize(String source) {
        return new Lexer(source).scan();
    }

    private List<Token> scan() {
        while (!atEnd()) {
            start = current;
            startLine = line;
            startColumn = column;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", line, column));
        return List.copyOf(tokens);
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case ' ', '\t', '\r', '\n' -> {
            }
            case '#' -> {
                while (!atEnd() && peek() != '\n') {
                    advance();
                }
            }
            case '(' -> add(TokenType.LPAREN);
            case ')' -> add(TokenType.RPAREN);
            case '.' -> add(TokenType.DOT);
            case '>' -> add(match('=') ? TokenType.GTE : TokenType.GT);
            case '<' -> add(match('=') ? TokenType.LTE : TokenType.LT);
            case '=' -> {
                if (!match('=')) {
                    throw error("Expected '==', found '='");
                }
                add(TokenType.EQ);
            }
            case '!' -> {
                if (!match('=')) {
                    throw error("Expected '!=', found '!'");
                }
                add(TokenType.NEQ);
            }
            case '"' -> string();
            default -> {
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    throw error("Unexpected character '" + c + "'");
                }
            }
        }
    }

    private void string() {
        while (!atEnd() && peek() != '"') {
            advance();
        }

        if (atEnd()) {
            throw error("Unterminated string");
        }

        advance();
        add(TokenType.STRING, source.substring(start + 1, current - 1));
    }

    private void number() {
        while (isDigit(peek())) {
            advance();
        }

        if (isDurationUnit(peek())) {
            advance();
            if (isAlpha(peek()) || isDigit(peek())) {
                throw error("Invalid duration");
            }
            add(TokenType.DURATION);
            return;
        }

        if (isAlpha(peek())) {
            throw error("Invalid number");
        }

        add(TokenType.NUMBER);
    }

    private void identifier() {
        while (isAlpha(peek()) || isDigit(peek())) {
            advance();
        }

        String text = source.substring(start, current);
        add(KEYWORDS.getOrDefault(text, TokenType.IDENT));
    }

    private char advance() {
        char c = source.charAt(current++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    private boolean match(char expected) {
        if (atEnd() || source.charAt(current) != expected) {
            return false;
        }
        advance();
        return true;
    }

    private char peek() {
        return atEnd() ? '\0' : source.charAt(current);
    }

    private boolean atEnd() {
        return current >= source.length();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isDurationUnit(char c) {
        return c == 's' || c == 'm' || c == 'h';
    }

    private void add(TokenType type) {
        add(type, source.substring(start, current));
    }

    private void add(TokenType type, String lexeme) {
        tokens.add(new Token(type, lexeme, startLine, startColumn));
    }

    private DslSyntaxException error(String message) {
        return new DslSyntaxException(message, startLine, startColumn);
    }
}
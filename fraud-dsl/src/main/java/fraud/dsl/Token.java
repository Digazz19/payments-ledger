package fraud.dsl;

public record Token(TokenType type, String lexeme, int line, int column) {
}
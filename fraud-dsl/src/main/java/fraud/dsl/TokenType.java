package fraud.dsl;

public enum TokenType {
    // Palavras reservadas
    RULE, WHEN, THEN, AND, OR, NOT, REVIEW, BLOCK, VELOCITY,

    // Literais e nomes
    IDENT, NUMBER, STRING, DURATION,

    // Pontuação
    DOT, LPAREN, RPAREN,

    // Operadores
    GT, GTE, LT, LTE, EQ, NEQ,

    EOF
}
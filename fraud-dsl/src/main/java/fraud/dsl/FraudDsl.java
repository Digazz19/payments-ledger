package fraud.dsl;

public final class FraudDsl {

    private FraudDsl() {
    }

    public static RuleSet parse(String source) {
        return Parser.parse(Lexer.tokenize(source));
    }
}
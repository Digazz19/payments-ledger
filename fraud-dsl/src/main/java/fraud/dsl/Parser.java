package fraud.dsl;

import static fraud.dsl.TokenType.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class Parser {

    private final List<Token> tokens;
    private int current = 0;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static RuleSet parse(List<Token> tokens) {
        return new Parser(tokens).ruleSet();
    }

    // ruleset = { rule } EOF
    private RuleSet ruleSet() {
        List<Rule> rules = new ArrayList<>();
        while (!check(EOF)) {
            rules.add(rule());
        }
        return new RuleSet(List.copyOf(rules));
    }

    // rule = "RULE" IDENT "WHEN" expr "THEN" action
    private Rule rule() {
        Token keyword = expect(RULE, "'RULE'");
        Token name = expect(IDENT, "a rule name");
        expect(WHEN, "'WHEN'");
        Expr condition = expr();
        expect(THEN, "'THEN'");
        Action action = action();
        return new Rule(name.lexeme(), condition, action, position(keyword));
    }

    // action = "REVIEW" | "BLOCK"
    private Action action() {
        if (match(REVIEW)) {
            return Action.REVIEW;
        }
        if (match(BLOCK)) {
            return Action.BLOCK;
        }
        throw error(peek(), "'REVIEW' or 'BLOCK'");
    }

    // expr = andExpr { "OR" andExpr }
    private Expr expr() {
        Expr left = andExpr();
        while (match(OR)) {
            left = new Expr.Or(left, andExpr());
        }
        return left;
    }

    // andExpr = notExpr { "AND" notExpr }
    private Expr andExpr() {
        Expr left = notExpr();
        while (match(AND)) {
            left = new Expr.And(left, notExpr());
        }
        return left;
    }

    // notExpr = "NOT" notExpr | primary
    private Expr notExpr() {
        if (match(NOT)) {
            return new Expr.Not(notExpr());
        }
        return primary();
    }

    // primary = "(" expr ")" | comparison
    private Expr primary() {
        if (match(LPAREN)) {
            Expr inner = expr();
            expect(RPAREN, "')'");
            return inner;
        }
        return comparison();
    }

    // comparison = operand compareOp operand
    private Expr comparison() {
        Token start = peek();
        Operand left = operand();
        CompareOp op = compareOp();
        Operand right = operand();
        return new Expr.Comparison(left, op, right, position(start));
    }

    private CompareOp compareOp() {
        Token token = peek();
        CompareOp op = switch (token.type()) {
            case GT -> CompareOp.GT;
            case GTE -> CompareOp.GTE;
            case LT -> CompareOp.LT;
            case LTE -> CompareOp.LTE;
            case EQ -> CompareOp.EQ;
            case NEQ -> CompareOp.NEQ;
            default -> throw error(token, "a comparison operator");
        };
        advance();
        return op;
    }

    // operand = field | velocity | NUMBER | STRING
    private Operand operand() {
        Token token = peek();
        switch (token.type()) {
            case IDENT -> {
                advance();
                List<String> path = new ArrayList<>(List.of(token.lexeme()));
                while (match(DOT)) {
                    path.add(expect(IDENT, "a field name").lexeme());
                }
                return new Operand.FieldRef(List.copyOf(path), position(token));
            }
            case VELOCITY -> {
                advance();
                expect(LPAREN, "'('");
                Token window = expect(DURATION, "a duration such as 30s, 1m or 1h");
                expect(RPAREN, "')'");
                return new Operand.Velocity(duration(window), position(token));
            }
            case NUMBER -> {
                advance();
                return new Operand.NumberLit(number(token));
            }
            case STRING -> {
                advance();
                return new Operand.StringLit(token.lexeme());
            }
            default -> throw error(token, "a field, number, string or velocity(...)");
        }
    }

    private long number(Token token) {
        try {
            return Long.parseLong(token.lexeme());
        } catch (NumberFormatException tooLarge) {
            throw new DslSyntaxException("Number too large", token.line(), token.column());
        }
    }

    private Duration duration(Token token) {
        String text = token.lexeme();
        long amount = Long.parseLong(text.substring(0, text.length() - 1));
        return switch (text.charAt(text.length() - 1)) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            default -> throw error(token, "a duration");
        };
    }

    // Auxiliares

    private Token expect(TokenType type, String description) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), description);
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private Token advance() {
        Token token = tokens.get(current);
        if (token.type() != EOF) {
            current++;
        }
        return token;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Position position(Token token) {
        return new Position(token.line(), token.column());
    }

    private DslSyntaxException error(Token found, String expected) {
        String description = found.type() == EOF ? "end of input" : "'" + found.lexeme() + "'";
        return new DslSyntaxException(
            "Expected " + expected + ", found " + description,
            found.line(),
            found.column());
    }
}
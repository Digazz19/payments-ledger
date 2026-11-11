package fraud.dsl;

public record Rule(String name, Expr condition, Action action, Position position) {
}
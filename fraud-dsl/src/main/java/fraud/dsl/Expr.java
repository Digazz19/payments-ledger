package fraud.dsl;

public sealed interface Expr {

    record And(Expr left, Expr right) implements Expr {
    }

    record Or(Expr left, Expr right) implements Expr {
    }

    record Not(Expr operand) implements Expr {
    }

    record Comparison(Operand left, CompareOp op, Operand right, Position position) implements Expr {
    }
}
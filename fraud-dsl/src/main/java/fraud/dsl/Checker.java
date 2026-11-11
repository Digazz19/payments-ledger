package fraud.dsl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class Checker {

    private enum Type {
        NUMBER,
        STRING,
        UNKNOWN
    }

    private static final Map<String, Type> FIELDS = Map.of(
        "amount", Type.NUMBER,
        "source.ageDays", Type.NUMBER,
        "source.country", Type.STRING,
        "target.country", Type.STRING
    );

    private static final Duration MAX_WINDOW = Duration.ofHours(1);

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private Checker() {
    }

    public static List<Diagnostic> check(RuleSet ruleSet) {
        Checker checker = new Checker();
        checker.checkRuleSet(ruleSet);
        return List.copyOf(checker.diagnostics);
    }

    private void checkRuleSet(RuleSet ruleSet) {
        Set<String> names = new HashSet<>();
        for (Rule rule : ruleSet.rules()) {
            if (!names.add(rule.name())) {
                report("DUPLICATE_RULE", "Rule '" + rule.name() + "' is defined more than once", rule.position());
            }
            checkExpr(rule.condition());
        }
    }

    private void checkExpr(Expr expr) {
        switch (expr) {
            case Expr.And and -> {
                checkExpr(and.left());
                checkExpr(and.right());
            }
            case Expr.Or or -> {
                checkExpr(or.left());
                checkExpr(or.right());
            }
            case Expr.Not not -> checkExpr(not.operand());
            case Expr.Comparison comparison -> checkComparison(comparison);
        }
    }

    private void checkComparison(Expr.Comparison comparison) {
        Type left = typeOf(comparison.left());
        Type right = typeOf(comparison.right());

        if (left == Type.UNKNOWN || right == Type.UNKNOWN) {
            return;
        }

        if (left != right) {
            report(
                "TYPE_MISMATCH",
                "Cannot compare " + describe(left) + " with " + describe(right),
                comparison.position());
            return;
        }

        if (left == Type.STRING && isOrdering(comparison.op())) {
            report(
                "INVALID_OPERATOR",
                "Text can only be compared with == or !=",
                comparison.position());
        }
    }

    private Type typeOf(Operand operand) {
        return switch (operand) {
            case Operand.FieldRef field -> {
                String name = String.join(".", field.path());
                Type type = FIELDS.get(name);
                if (type == null) {
                    report(
                        "UNKNOWN_FIELD",
                        "Unknown field '" + name + "'. Valid fields: " + String.join(", ", new TreeSet<>(FIELDS.keySet())),
                        field.position());
                    yield Type.UNKNOWN;
                }
                yield type;
            }
            case Operand.Velocity velocity -> {
                checkWindow(velocity);
                yield Type.NUMBER;
            }
            case Operand.NumberLit number -> Type.NUMBER;
            case Operand.StringLit string -> Type.STRING;
        };
    }

    private void checkWindow(Operand.Velocity velocity) {
        Duration window = velocity.window();
        if (window.isZero()) {
            report("INVALID_WINDOW", "The velocity window must be greater than zero", velocity.position());
        } else if (window.compareTo(MAX_WINDOW) > 0) {
            report("WINDOW_TOO_LARGE", "The velocity window can be at most 1h", velocity.position());
        }
    }

    private boolean isOrdering(CompareOp op) {
        return op == CompareOp.GT || op == CompareOp.GTE || op == CompareOp.LT || op == CompareOp.LTE;
    }

    private String describe(Type type) {
        return type == Type.NUMBER ? "a number" : "text";
    }

    private void report(String code, String message, Position position) {
        diagnostics.add(new Diagnostic(code, message, position));
    }
}
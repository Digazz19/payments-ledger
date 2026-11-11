package fraud.dsl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Evaluator {

    private final EvaluationContext context;
    private final Map<Duration, Long> velocityCache = new HashMap<>();

    private Evaluator(EvaluationContext context) {
        this.context = context;
    }

    public static Evaluation evaluate(RuleSet ruleSet, EvaluationContext context) {
        return new Evaluator(context).run(ruleSet);
    }

    private Evaluation run(RuleSet ruleSet) {
        Decision decision = Decision.APPROVE;
        List<String> matched = new ArrayList<>();

        for (Rule rule : ruleSet.rules()) {
            if (eval(rule.condition())) {
                matched.add(rule.name());
                Decision ruleDecision = toDecision(rule.action());
                if (ruleDecision.compareTo(decision) > 0) {
                    decision = ruleDecision;
                }
            }
        }

        return new Evaluation(decision, List.copyOf(matched));
    }

    private boolean eval(Expr expr) {
        return switch (expr) {
            case Expr.And and -> eval(and.left()) && eval(and.right());
            case Expr.Or or -> eval(or.left()) || eval(or.right());
            case Expr.Not not -> !eval(not.operand());
            case Expr.Comparison comparison -> compare(
                value(comparison.left()),
                comparison.op(),
                value(comparison.right()));
        };
    }

    private Object value(Operand operand) {
        return switch (operand) {
            case Operand.FieldRef field -> field(field);
            case Operand.Velocity velocity -> velocityCache.computeIfAbsent(
                velocity.window(),
                window -> context.velocity().count(window));
            case Operand.NumberLit number -> number.value();
            case Operand.StringLit string -> string.value();
        };
    }

    private Object field(Operand.FieldRef field) {
        String name = String.join(".", field.path());
        return switch (name) {
            case "amount" -> context.amountMinor();
            case "source.ageDays" -> context.sourceAgeDays();
            case "source.country" -> context.sourceCountry();
            case "target.country" -> context.targetCountry();
            default -> throw new IllegalStateException("Unknown field '" + name + "'. Run the checker first.");
        };
    }

    private boolean compare(Object left, CompareOp op, Object right) {
        if (left instanceof Long l && right instanceof Long r) {
            int result = Long.compare(l, r);
            return switch (op) {
                case GT -> result > 0;
                case GTE -> result >= 0;
                case LT -> result < 0;
                case LTE -> result <= 0;
                case EQ -> result == 0;
                case NEQ -> result != 0;
            };
        }

        if (left instanceof String l && right instanceof String r) {
            return switch (op) {
                case EQ -> l.equals(r);
                case NEQ -> !l.equals(r);
                default -> throw new IllegalStateException("Invalid operator for text. Run the checker first.");
            };
        }

        throw new IllegalStateException("Incompatible types. Run the checker first.");
    }

    private Decision toDecision(Action action) {
        return switch (action) {
            case REVIEW -> Decision.REVIEW;
            case BLOCK -> Decision.BLOCK;
        };
    }
}
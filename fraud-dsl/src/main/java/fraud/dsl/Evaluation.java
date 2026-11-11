package fraud.dsl;

import java.util.List;

public record Evaluation(Decision decision, List<String> matchedRules) {
}
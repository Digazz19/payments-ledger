package ledger.fraud;

import java.util.List;

public record FraudVerdict(FraudDecision decision, List<String> matchedRules) {

    public static FraudVerdict unavailable() {
        return new FraudVerdict(FraudDecision.UNAVAILABLE, List.of());
    }
}
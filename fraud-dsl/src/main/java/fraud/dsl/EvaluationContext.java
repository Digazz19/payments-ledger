package fraud.dsl;

public record EvaluationContext(
    long amountMinor,
    String sourceCountry,
    String targetCountry,
    long sourceAgeDays,
    VelocityCounter velocity
) {
}
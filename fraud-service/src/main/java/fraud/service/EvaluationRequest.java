package fraud.service;

public record EvaluationRequest(
    String transferId,
    String sourceAccountId,
    Long amountMinor,
    String sourceCountry,
    String targetCountry,
    Long sourceAgeDays
) {
}
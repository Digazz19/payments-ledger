package ledger.idempotency;

public record IdempotencyRecord(String requestHash, String state, Integer responseStatus, String responseBody) {
}
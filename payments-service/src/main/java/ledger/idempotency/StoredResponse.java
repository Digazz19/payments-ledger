package ledger.idempotency;

public record StoredResponse(int status, String body) {
}
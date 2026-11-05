package ledger.service;

public record CreateAccountRequest(String ownerName, String currency, String country) {
}
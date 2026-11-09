package ledger.service;

import java.util.UUID;

public record TransferRequest(UUID sourceAccountId, UUID targetAccountId, Long amountMinor, String currency) {
}
package ledger.service;

import java.util.UUID;

public record DepositRequest(UUID accountId, Long amountMinor) {
}
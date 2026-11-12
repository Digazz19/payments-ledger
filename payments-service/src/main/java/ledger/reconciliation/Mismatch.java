package ledger.reconciliation;

import java.util.UUID;

public record Mismatch(UUID accountId, long balanceMinor, long ledgerTotal) {
}
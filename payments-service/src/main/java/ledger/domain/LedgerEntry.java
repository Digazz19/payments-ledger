package ledger.domain;

import java.util.UUID;

public record LedgerEntry(UUID accountId, Direction direction, long amountMinor) {

    public long signedAmount() {
        return direction == Direction.CREDIT ? amountMinor : -amountMinor;
    }
}
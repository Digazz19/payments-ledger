package ledger.domain;

import java.util.UUID;

public record LedgerEntry(UUID accountId, Direction direction, long amountMinor) {

    public long signedAmount() {
        if (direction == Direction.CREDIT) {
            return amountMinor;
        }
        else {
            return -amountMinor;
        }
    }
}
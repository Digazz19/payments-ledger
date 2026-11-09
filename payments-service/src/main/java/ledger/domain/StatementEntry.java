package ledger.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StatementEntry(
    long id,
    UUID transferId,
    String direction,
    long amountMinor,
    OffsetDateTime createdAt
) {
}
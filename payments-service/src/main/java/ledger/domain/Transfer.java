package ledger.domain;

import java.util.UUID;

public record Transfer(
    UUID id,
    UUID sourceAccountId,
    UUID targetAccountId,
    long amountMinor,
    String currency,
    TransferStatus status,
    String reason
) {
}
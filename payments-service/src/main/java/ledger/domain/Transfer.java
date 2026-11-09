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

    public TransferOutcome outcome() {
        return switch (status) {
            case COMPLETED -> new TransferOutcome.Completed();
            case REJECTED -> new TransferOutcome.Rejected(reason);
            case IN_REVIEW -> new TransferOutcome.InReview(reason);
        };
    }
}
package ledger.http;

import ledger.domain.TransferOutcome;

public final class HttpMapping {

    private HttpMapping() {
    }

    public static int statusFor(TransferOutcome outcome) {
        return switch (outcome) {
            case TransferOutcome.Completed completed -> 201;
            case TransferOutcome.Rejected rejected -> 422;
            case TransferOutcome.InReview inReview -> 202;
        };
    }
}
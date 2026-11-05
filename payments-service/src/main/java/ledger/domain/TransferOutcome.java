package ledger.domain;

public sealed interface TransferOutcome {

    record Completed() implements TransferOutcome {
    }

    record Rejected(String reason) implements TransferOutcome {
    }

    record InReview(String reason) implements TransferOutcome {
    }
}
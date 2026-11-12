package ledger.service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BiConsumer;
import ledger.domain.Account;
import ledger.domain.Transfer;
import ledger.domain.TransferStatus;
import ledger.fraud.FraudClient;
import ledger.fraud.FraudRequest;
import ledger.fraud.FraudVerdict;
import ledger.http.ApiException;
import ledger.repository.TransferRepository;
import org.jooq.DSLContext;

public class TransferService {

    private final AccountService accounts;
    private final TransferRepository repository;
    private final FraudClient fraud;
    private final Clock clock;

    public TransferService(AccountService accounts, TransferRepository repository, FraudClient fraud, Clock clock) {
        this.accounts = accounts;
        this.repository = repository;
        this.fraud = fraud;
        this.clock = clock;
    }

    public Transfer transfer(TransferRequest request, BiConsumer<DSLContext, Transfer> beforeCommit) {
        validate(request);

        Account source = accounts.get(request.sourceAccountId());
        Account target = accounts.get(request.targetAccountId());
        UUID id = UUID.randomUUID();

        FraudVerdict verdict = fraud.evaluate(new FraudRequest(
            id,
            source.id(),
            request.amountMinor(),
            source.country(),
            target.country(),
            ageInDays(source)));

        return switch (verdict.decision()) {
            case APPROVE -> repository.transfer(
                id,
                source.id(),
                target.id(),
                request.amountMinor(),
                request.currency(),
                beforeCommit);
            case REVIEW -> hold(id, request, TransferStatus.IN_REVIEW, "RULE_" + firstRule(verdict), beforeCommit);
            case BLOCK -> hold(id, request, TransferStatus.REJECTED, "FRAUD_BLOCKED", beforeCommit);
            case UNAVAILABLE -> hold(id, request, TransferStatus.IN_REVIEW, "FRAUD_UNAVAILABLE", beforeCommit);
        };
    }

    public Transfer review(UUID id, ReviewRequest request) {
        ReviewDecision decision;
        try {
            decision = ReviewDecision.valueOf(String.valueOf(request.decision()));
        } catch (IllegalArgumentException invalid) {
            throw validationError("decision must be APPROVE or REJECT");
        }
        return repository.review(id, decision == ReviewDecision.APPROVE);
    }

    public Transfer get(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ApiException(404, "TRANSFER_NOT_FOUND", "Transfer not found"));
    }

    private Transfer hold(
            UUID id,
            TransferRequest request,
            TransferStatus status,
            String reason,
            BiConsumer<DSLContext, Transfer> beforeCommit) {
        Transfer transfer = new Transfer(
            id,
            request.sourceAccountId(),
            request.targetAccountId(),
            request.amountMinor(),
            request.currency(),
            status,
            reason);
        repository.save(transfer, beforeCommit);
        return transfer;
    }

    private long ageInDays(Account account) {
        return Duration.between(account.createdAt().toInstant(), clock.instant()).toDays();
    }

    private String firstRule(FraudVerdict verdict) {
        return verdict.matchedRules().isEmpty() ? "UNKNOWN" : verdict.matchedRules().get(0);
    }

    private void validate(TransferRequest request) {
        if (request.sourceAccountId() == null || request.targetAccountId() == null) {
            throw validationError("sourceAccountId and targetAccountId are required");
        }
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw validationError("source and target accounts must be different");
        }
        if (request.amountMinor() == null || request.amountMinor() <= 0) {
            throw validationError("amountMinor must be positive");
        }
        if (!"EUR".equals(request.currency())) {
            throw validationError("currency must be EUR");
        }
    }

    private ApiException validationError(String message) {
        return new ApiException(400, "VALIDATION_ERROR", message);
    }
}
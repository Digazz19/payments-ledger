package ledger.service;

import java.util.UUID;
import ledger.domain.Account;
import ledger.domain.Transfer;
import ledger.domain.TransferStatus;
import ledger.http.ApiException;
import ledger.repository.TransferRepository;

public class TransferService {

    private final AccountService accounts;
    private final TransferRepository repository;

    public TransferService(AccountService accounts, TransferRepository repository) {
        this.accounts = accounts;
        this.repository = repository;
    }

    public Transfer transfer(TransferRequest request) {
        validate(request);

        Account source = accounts.get(request.sourceAccountId());
        accounts.get(request.targetAccountId());

        boolean hasFunds = source.balanceMinor() >= request.amountMinor();

        Transfer transfer = new Transfer(
            UUID.randomUUID(),
            request.sourceAccountId(),
            request.targetAccountId(),
            request.amountMinor(),
            request.currency(),
            hasFunds ? TransferStatus.COMPLETED : TransferStatus.REJECTED,
            hasFunds ? null : "INSUFFICIENT_FUNDS"
        );

        repository.save(transfer);
        return transfer;
    }

    public Transfer get(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ApiException(404, "TRANSFER_NOT_FOUND", "Transfer not found"));
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
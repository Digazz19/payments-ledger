package ledger.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import ledger.domain.Account;
import ledger.http.ApiException;
import ledger.repository.AccountRepository;

public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public Account create(CreateAccountRequest request) {
        validate(request);

        Account account = new Account(
            UUID.randomUUID(),
            request.ownerName(),
            request.currency(),
            request.country(),
            0,
            OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS)
        );

        repository.insert(account);
        return account;
    }

    public Account get(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ApiException(404, "ACCOUNT_NOT_FOUND", "Account not found"));
    }

    private void validate(CreateAccountRequest request) {
        if (request.ownerName() == null || request.ownerName().isBlank()) {
            throw validationError("OwnerName is required");
        }
        if (!"EUR".equals(request.currency())) {
            throw validationError("Currency must be EUR");
        }
        if (request.country() == null || !request.country().matches("[A-Z]{2}")) {
            throw validationError("Country must be a two-letter code");
        }
    }

    private ApiException validationError(String message) {
        return new ApiException(400, "VALIDATION_ERROR", message);
    }
}
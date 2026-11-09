package ledger.service;

import java.util.UUID;
import ledger.domain.Page;
import ledger.domain.StatementEntry;
import ledger.http.ApiException;
import ledger.repository.LedgerEntryRepository;

public class StatementService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AccountService accounts;
    private final LedgerEntryRepository repository;

    public StatementService(AccountService accounts, LedgerEntryRepository repository) {
        this.accounts = accounts;
        this.repository = repository;
    }

    public Page<StatementEntry> entries(UUID accountId, String limitParam, String cursorParam) {
        accounts.get(accountId);

        int limit = limitParam == null ? DEFAULT_LIMIT : parseLong(limitParam, "limit").intValue();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw validationError("limit must be between 1 and " + MAX_LIMIT);
        }

        Long cursor = cursorParam == null ? null : parseLong(cursorParam, "cursor");

        return repository.findByAccount(accountId, cursor, limit);
    }

    private Long parseLong(String value, String name) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw validationError(name + " must be a number");
        }
    }

    private ApiException validationError(String message) {
        return new ApiException(400, "VALIDATION_ERROR", message);
    }
}
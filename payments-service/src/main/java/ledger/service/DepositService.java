package ledger.service;

import java.util.UUID;
import java.util.function.BiConsumer;
import ledger.domain.Account;
import ledger.domain.SystemAccount;
import ledger.domain.Transfer;
import ledger.domain.TransferStatus;
import ledger.http.ApiException;
import ledger.repository.TransferRepository;
import org.jooq.DSLContext;

public class DepositService {

    private final AccountService accounts;
    private final TransferRepository transfers;

    public DepositService(AccountService accounts, TransferRepository transfers) {
        this.accounts = accounts;
        this.transfers = transfers;
    }

    public Transfer deposit(DepositRequest request, BiConsumer<DSLContext, Transfer> beforeCommit) {
        if (request.accountId() == null) {
            throw new ApiException(400, "VALIDATION_ERROR", "accountId is required");
        }
        if (request.amountMinor() == null || request.amountMinor() <= 0) {
            throw new ApiException(400, "VALIDATION_ERROR", "amountMinor must be positive");
        }

        Account account = accounts.get(request.accountId());

        Transfer transfer = new Transfer(
            UUID.randomUUID(),
            SystemAccount.ID,
            account.id(),
            request.amountMinor(),
            account.currency(),
            TransferStatus.COMPLETED,
            null
        );

        transfers.save(transfer, beforeCommit);
        return transfer;
    }
}
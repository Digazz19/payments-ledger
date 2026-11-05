package ledger.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InMemoryLedger {

    private static final UUID SYSTEM_ACCOUNT = new UUID(0, 1);

    private final List<LedgerEntry> entries = new ArrayList<>();

    public UUID createAccount() {
        return UUID.randomUUID();
    }

    public void deposit(UUID accountId, long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("AmountMinor must be positive");
        }

        entries.add(new LedgerEntry(SYSTEM_ACCOUNT, Direction.DEBIT, amountMinor));
        entries.add(new LedgerEntry(accountId, Direction.CREDIT, amountMinor));
    }

    public void transfer(UUID source, UUID target, long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }

        if (balance(source) < amountMinor) {
            throw new InsufficientFundsException();
        }

        entries.add(new LedgerEntry(source, Direction.DEBIT, amountMinor));
        entries.add(new LedgerEntry(target, Direction.CREDIT, amountMinor));
    }

    public long balance(UUID accountId) {
        return entries.stream()
            .filter(entry -> entry.accountId().equals(accountId))
            .mapToLong(LedgerEntry::signedAmount)
            .sum();
    }

    public List<LedgerEntry> entries() {
        return List.copyOf(entries);
    }
}
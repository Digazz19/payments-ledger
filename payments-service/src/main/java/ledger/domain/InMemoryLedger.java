package ledger.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class InMemoryLedger {

    private static final UUID SYSTEM_ACCOUNT = new UUID(0, 1);

    private final Collection<LedgerEntry> entries = new ConcurrentLinkedQueue<>();
    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    private ReentrantLock lockFor(UUID accountId) {
        return locks.computeIfAbsent(accountId, id -> new ReentrantLock());
    }

    public UUID createAccount() {
        return UUID.randomUUID();
    }

    public void deposit(UUID accountId, long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("AmountMinor must be positive");
        }

        ReentrantLock lock = lockFor(accountId);
        lock.lock();
        try {
            entries.add(new LedgerEntry(SYSTEM_ACCOUNT, Direction.DEBIT, amountMinor));
            entries.add(new LedgerEntry(accountId, Direction.CREDIT, amountMinor));
        } finally {
            lock.unlock();
        }
    }

    public void transfer(UUID source, UUID target, long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("AmountMinor must be positive");
        }

        UUID first = source.compareTo(target) < 0 ? source : target;
        UUID second = first.equals(source) ? target : source;

        ReentrantLock firstLock = lockFor(first);
        ReentrantLock secondLock = lockFor(second);

        firstLock.lock();
        secondLock.lock();
        try {
            if (balance(source) < amountMinor) {
                throw new InsufficientFundsException();
            }

            entries.add(new LedgerEntry(source, Direction.DEBIT, amountMinor));
            entries.add(new LedgerEntry(target, Direction.CREDIT, amountMinor));
        } finally {
            secondLock.unlock();
            firstLock.unlock();
        }
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
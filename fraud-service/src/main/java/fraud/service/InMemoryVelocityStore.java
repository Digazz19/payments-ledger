package fraud.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryVelocityStore implements VelocityStore {

    private final Map<String, Map<String, Instant>> byAccount = new ConcurrentHashMap<>();

    @Override
    public void record(String accountId, String transferId, Instant at) {
        byAccount
            .computeIfAbsent(accountId, id -> new ConcurrentHashMap<>())
            .putIfAbsent(transferId, at);
    }

    @Override
    public long count(String accountId, Duration window, Instant now) {
        Instant from = now.minus(window);
        return byAccount.getOrDefault(accountId, Map.of()).values().stream()
            .filter(at -> at.isAfter(from) && !at.isAfter(now))
            .count();
    }
}
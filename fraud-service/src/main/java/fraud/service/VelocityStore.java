package fraud.service;

import java.time.Duration;
import java.time.Instant;

public interface VelocityStore {

    void record(String accountId, String transferId, Instant at);

    long count(String accountId, Duration window, Instant now);
}
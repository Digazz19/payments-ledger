package ledger.domain;

import java.util.UUID;

public record Account(UUID id, String ownerName, String currency, String country, long balanceMinor) {
}
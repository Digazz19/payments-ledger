package ledger.fraud;

import java.util.UUID;

public record FraudRequest(
    UUID transferId,
    UUID sourceAccountId,
    long amountMinor,
    String sourceCountry,
    String targetCountry,
    long sourceAgeDays
) {
}
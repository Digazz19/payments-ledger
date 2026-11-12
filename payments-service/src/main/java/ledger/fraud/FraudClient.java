package ledger.fraud;

public interface FraudClient {
    FraudVerdict evaluate(FraudRequest request);
}
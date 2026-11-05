package ledger.domain;

public record Money(long amountMinor, String currency) {
    public Money {
        if (amountMinor < 0) {
            throw new IllegalArgumentException("amountMinor must not be negative");
        }
        if (!"EUR".equals(currency)) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }
}
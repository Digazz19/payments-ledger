package ledger.domain

import spock.lang.Specification

class MoneySpec extends Specification {
    def "soma e subtrai"() {
        expect:
        new Money(100, "EUR").plus(new Money(50, "EUR")) == new Money(150, "EUR")
        new Money(100, "EUR").minus(new Money(40, "EUR")) == new Money(60, "EUR")
    }

    def "recusa valores invalidos"() {
        when:
        new Money(amount, currency)

        then:
        thrown(IllegalArgumentException)

        where:
        amount | currency
        -1     | "EUR"
        100    | "USD"
    }

    def "recusa um resultado negativo"() {
        when:
        new Money(10, "EUR").minus(new Money(11, "EUR"))

        then:
        thrown(IllegalArgumentException)
    }

    def "deteta overflow"() {
        when:
        new Money(Long.MAX_VALUE, "EUR").plus(new Money(1, "EUR"))

        then:
        thrown(ArithmeticException)
    }
}
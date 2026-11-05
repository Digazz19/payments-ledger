package ledger.domain

import spock.lang.Specification

class InMemoryLedgerSpec extends Specification {
    def ledger = new InMemoryLedger()

    def "deposito cria um debito e um credito"() {
        given:
        def a = ledger.createAccount()

        when:
        ledger.deposit(a, 1000)

        then:
        ledger.balance(a) == 1000
        ledger.entries().size() == 2
        ledger.entries()*.direction().toSet() == [Direction.DEBIT, Direction.CREDIT].toSet()
    }

    def "transfere e mantem o total"() {
        given:
        def a = ledger.createAccount()
        def b = ledger.createAccount()
        ledger.deposit(a, 1000)

        when:
        ledger.transfer(a, b, 300)

        then:
        ledger.balance(a) == 700
        ledger.balance(b) == 300
    }

    def "recusa transferencia sem saldo e nao cria entradas"() {
        given:
        def a = ledger.createAccount()
        def b = ledger.createAccount()
        def before = ledger.entries().size()

        when:
        ledger.transfer(a, b, 1)

        then:
        thrown(InsufficientFundsException)
        ledger.entries().size() == before
    }

    def "a soma de todas as entradas e zero"() {
        given:
        def a = ledger.createAccount()
        def b = ledger.createAccount()
        ledger.deposit(a, 1000)
        ledger.transfer(a, b, 300)

        expect:
        ledger.entries().sum { it.signedAmount() } == 0
    }
}
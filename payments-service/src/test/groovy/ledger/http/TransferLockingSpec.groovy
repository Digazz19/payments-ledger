package ledger.http

import spock.lang.Timeout

class TransferLockingSpec extends ApiSpec {
    @Timeout(90)
    def "transferencias opostas nao criam deadlocks"() {
        given:
        def a = newAccount(balance: 10_000)
        def b = newAccount(balance: 10_000)

        when:
        def responses = concurrently(1000) { i ->
            i % 2 == 0
                ? client.post("/transfers", transfer(a, b, 1))
                : client.post("/transfers", transfer(b, a, 1))
        }

        then:
        responses.every { it.status == 201 }
        balance(a) == 10_000
        balance(b) == 10_000
    }

    def "contas diferentes nao se bloqueiam entre si"() {
        given:
        def pairs = (1..4).collect { [newAccount(balance: 100), newAccount()] }

        when:
        def responses = concurrently(4) { i ->
            client.post("/transfers", transfer(pairs[i][0], pairs[i][1], 10))
        }

        then:
        responses.every { it.status == 201 }
    }
}
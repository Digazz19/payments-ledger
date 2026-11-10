package ledger.http

import spock.lang.Timeout

class IdempotencySpec extends ApiSpec {
    String newKey() {
        UUID.randomUUID().toString()
    }

    def "repetir devolve a mesma resposta e cobra uma vez"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()
        def body = transfer(a, b, 100)
        def key = newKey()

        when:
        def first = client.post("/transfers", body, ["Idempotency-Key": key])
        def second = client.post("/transfers", body, ["Idempotency-Key": key])

        then:
        first.status == 201
        second.status == first.status
        second.json == first.json
        balance(a) == 900
    }

    def "recusa a mesma chave com outro corpo"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()
        def key = newKey()
        client.post("/transfers", transfer(a, b, 100), ["Idempotency-Key": key])

        when:
        def r = client.post("/transfers", transfer(a, b, 200), ["Idempotency-Key": key])

        then:
        r.status == 422
        r.json.code == "IDEMPOTENCY_KEY_REUSED"
        balance(a) == 900
    }

    def "recusa a mesma chave noutro endpoint"() {
        given:
        def a = newAccount()
        def key = newKey()
        client.post("/deposits", [accountId: a, amountMinor: 100], ["Idempotency-Key": key])

        when:
        def r = client.post("/transfers", transfer(a, newAccount(), 10), ["Idempotency-Key": key])

        then:
        r.status == 422
        balance(a) == 100
    }

    def "exige a chave"() {
        when:
        def r = client.postWithoutKey("/transfers", transfer(newAccount(), newAccount(), 1))

        then:
        r.status == 400
        r.json.code == "IDEMPOTENCY_KEY_REQUIRED"
    }

    def "repetir uma recusa nao volta a avaliar"() {
        given:
        def a = newAccount(balance: 10)
        def b = newAccount()
        def body = transfer(a, b, 50)
        def key = newKey()
        client.post("/transfers", body, ["Idempotency-Key": key])
        client.post("/deposits", [accountId: a, amountMinor: 100])

        when:
        def r = client.post("/transfers", body, ["Idempotency-Key": key])

        then:
        r.status == 422
        r.json.reason == "INSUFFICIENT_FUNDS"
        balance(a) == 110
    }

    def "repetir um erro de validacao devolve o mesmo erro"() {
        given:
        def body = transfer(newAccount(), newAccount(), 0)
        def key = newKey()

        when:
        def first = client.post("/transfers", body, ["Idempotency-Key": key])
        def second = client.post("/transfers", body, ["Idempotency-Key": key])

        then:
        first.status == 400
        second.status == 400
        second.json == first.json
    }

    @Timeout(60)
    def "pedidos simultaneos com a mesma chave cobram uma vez"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()
        def body = transfer(a, b, 100)
        def key = newKey()

        when:
        def responses = concurrently(50) { i ->
            client.post("/transfers", body, ["Idempotency-Key": key])
        }

        then:
        responses.every { it.status in [201, 409] }
        responses.count { it.status == 201 } >= 1
        balance(a) == 900
    }
}
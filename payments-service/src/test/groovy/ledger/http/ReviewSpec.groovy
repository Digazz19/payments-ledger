package ledger.http

import spock.lang.Timeout

class ReviewSpec extends ApiSpec {
    String inReview(String source, String target, long amount) {
        fraudStub.respond("REVIEW", ["high_value_foreign"])
        def id = client.post("/transfers", transfer(source, target, amount)).json.id
        fraudStub.reset()
        id
    }

    def "aprovar move o dinheiro"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()
        def id = inReview(a, b, 100)

        when:
        def r = client.post("/transfers/$id/review", [decision: "APPROVE"])

        then:
        r.status == 200
        r.json.status == "COMPLETED"
        r.json.reason == null
        balance(a) == 900
        balance(b) == 100
        client.get("/transfers/$id").json.status == "COMPLETED"
    }

    def "rejeitar nao move dinheiro"() {
        given:
        def a = newAccount(balance: 1000)
        def id = inReview(a, newAccount(), 100)

        when:
        def r = client.post("/transfers/$id/review", [decision: "REJECT"])

        then:
        r.status == 200
        r.json.status == "REJECTED"
        r.json.reason == "REJECTED_BY_REVIEWER"
        balance(a) == 1000
    }

    def "aprovar sem saldo recusa a transferencia"() {
        given:
        def a = newAccount(balance: 100)
        def id = inReview(a, newAccount(), 100)
        client.post("/transfers", transfer(a, newAccount(), 60))

        when:
        def r = client.post("/transfers/$id/review", [decision: "APPROVE"])

        then:
        r.status == 422
        r.json.status == "REJECTED"
        r.json.reason == "INSUFFICIENT_FUNDS"
        balance(a) == 40
    }

    def "uma transferencia ja decidida nao volta a ser revista"() {
        given:
        def a = newAccount(balance: 1000)
        def id = inReview(a, newAccount(), 100)
        client.post("/transfers/$id/review", [decision: "APPROVE"])

        when:
        def r = client.post("/transfers/$id/review", [decision: "REJECT"])

        then:
        r.status == 409
        r.json.code == "TRANSFER_NOT_IN_REVIEW"
        balance(a) == 900
    }

    def "nao se pode rever uma transferencia concluida"() {
        given:
        def a = newAccount(balance: 1000)
        def id = client.post("/transfers", transfer(a, newAccount(), 100)).json.id

        expect:
        client.post("/transfers/$id/review", [decision: "APPROVE"]).status == 409
    }

    def "valida o pedido"() {
        given:
        def id = inReview(newAccount(balance: 1000), newAccount(), 100)

        expect:
        client.post("/transfers/$id/review", [decision: "MAYBE"]).status == 400
        client.post("/transfers/$id/review", [:]).status == 400
        client.post("/transfers/${UUID.randomUUID()}/review", [decision: "APPROVE"]).status == 404
    }

    @Timeout(30)
    def "aprovacoes simultaneas cobram uma vez"() {
        given:
        def a = newAccount(balance: 1000)
        def id = inReview(a, newAccount(), 100)

        when:
        def responses = concurrently(20) { i ->
            client.post("/transfers/$id/review", [decision: "APPROVE"])
        }

        then:
        responses.count { it.status == 200 } == 1
        responses.count { it.status == 409 } == 19
        balance(a) == 900
    }
}
package ledger.http

import java.time.Duration
import spock.lang.Timeout

class FraudIntegrationSpec extends ApiSpec {
    @Override
    Duration fraudTimeout() {
        Duration.ofMillis(300)
    }

    def "aplica cada decisao"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()
        fraudStub.respond(decision, ["high_value_foreign"])

        when:
        def r = client.post("/transfers", transfer(a, b, 100))

        then:
        r.status == status
        r.json.status == state
        r.json.reason == reason
        balance(a) == remaining

        where:
        decision  | status | state       | reason                       | remaining
        "APPROVE" | 201    | "COMPLETED" | null                         | 900
        "REVIEW"  | 202    | "IN_REVIEW" | "RULE_high_value_foreign"    | 1000
        "BLOCK"   | 422    | "REJECTED"  | "FRAUD_BLOCKED"              | 1000
    }

    def "envia os dados certos para a fraude"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount(country: "ES")

        when:
        def r = client.post("/transfers", transfer(a, b, 250))
        def sent = fraudStub.requests.last()

        then:
        sent.transferId == r.json.id
        sent.sourceAccountId == a
        sent.amountMinor == 250
        sent.sourceCountry == "PT"
        sent.targetCountry == "ES"
        sent.sourceAgeDays == 0
    }

    def "falha em seguranca quando a fraude nao responde"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()
        fraudStub.delay(2000)

        when:
        def started = System.nanoTime()
        def r = client.post("/transfers", transfer(a, b, 100))
        def elapsedMs = (System.nanoTime() - started) / 1_000_000

        then:
        r.status == 202
        r.json.status == "IN_REVIEW"
        r.json.reason == "FRAUD_UNAVAILABLE"
        balance(a) == 1000
        elapsedMs < 1000
    }

    def "falha em seguranca quando a fraude devolve erro"() {
        given:
        def a = newAccount(balance: 1000)
        fraudStub.failWith(503)

        when:
        def r = client.post("/transfers", transfer(a, newAccount(), 100))

        then:
        r.status == 202
        r.json.reason == "FRAUD_UNAVAILABLE"
        balance(a) == 1000
    }

    def "nao mantem transacoes abertas durante a chamada"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()
        def openTransactions = []
        fraudStub.onRequest = {
            openTransactions << sql.firstRow(
                "SELECT count(*) AS c FROM pg_stat_activity WHERE state = 'idle in transaction'"
            ).c
        }

        when:
        def r = client.post("/transfers", transfer(a, b, 100))

        then:
        r.status == 201
        openTransactions == [0]
    }

    @Timeout(30)
    def "chamadas lentas nao bloqueiam outras transferencias"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()
        fraudStub.delay(100)

        when:
        def responses = concurrently(10) { i -> client.post("/transfers", transfer(a, b, 1)) }

        then:
        responses.every { it.status == 201 }
        balance(a) == 990
    }

    def "os depositos nao passam pela fraude"() {
        when:
        newAccount(balance: 1000)

        then:
        fraudStub.requests.isEmpty()
    }

    def "repetir uma transferencia em revisao devolve a mesma resposta"() {
        given:
        def body = transfer(newAccount(balance: 1000), newAccount(), 100)
        def key = UUID.randomUUID().toString()
        fraudStub.respond("REVIEW", ["high_value_foreign"])

        when:
        def first = client.post("/transfers", body, ["Idempotency-Key": key])
        def second = client.post("/transfers", body, ["Idempotency-Key": key])

        then:
        first.status == 202
        second.json == first.json
        fraudStub.requests.size() == 1
    }
}
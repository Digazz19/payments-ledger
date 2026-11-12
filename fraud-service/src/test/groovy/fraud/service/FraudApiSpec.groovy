package fraud.service

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import spock.lang.Shared
import spock.lang.Specification

class FraudApiSpec extends Specification {
    static final String RULES = '''
        RULE high_value_foreign WHEN amount > 100000 AND source.country != target.country THEN REVIEW
        RULE rapid_fire WHEN velocity(1m) >= 5 THEN BLOCK
    '''

    @Shared
    FraudApp app

    @Shared
    HttpClient http = HttpClient.newHttpClient()

    def setupSpec() {
        app = FraudApp.start(RULES, new InMemoryVelocityStore(), Clock.systemUTC(), 0)
    }

    def cleanupSpec() {
        app?.stop()
    }

    Map evaluation(Map overrides = [:]) {
        [
            transferId: UUID.randomUUID().toString(),
            sourceAccountId: UUID.randomUUID().toString(),
            amountMinor: 1000,
            sourceCountry: "PT",
            targetCountry: "PT",
            sourceAgeDays: 100
        ] + overrides
    }

    Map post(Map body) {
        def request = HttpRequest.newBuilder(URI.create("http://localhost:${app.port()}/evaluations"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(body)))
            .build()
        def response = http.send(request, HttpResponse.BodyHandlers.ofString())
        [status: response.statusCode(), json: new JsonSlurper().parseText(response.body())]
    }

    def "responde ao health check"() {
        when:
        def request = HttpRequest.newBuilder(URI.create("http://localhost:${app.port()}/health")).build()

        then:
        http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200
    }

    def "aprova uma transacao normal"() {
        when:
        def r = post(evaluation())

        then:
        r.status == 200
        r.json.decision == "APPROVE"
        r.json.matchedRules == []
    }

    def "envia para revisao um valor elevado para outro pais"() {
        when:
        def r = post(evaluation(amountMinor: 200_000, targetCountry: "ES"))

        then:
        r.status == 200
        r.json.decision == "REVIEW"
        r.json.matchedRules == ["high_value_foreign"]
    }

    def "bloqueia a quinta transferencia da mesma conta num minuto"() {
        given:
        def account = UUID.randomUUID().toString()

        when:
        def decisions = (1..5).collect { post(evaluation(sourceAccountId: account)).json.decision }

        then:
        decisions == ["APPROVE", "APPROVE", "APPROVE", "APPROVE", "BLOCK"]
    }

    def "a mesma transferencia repetida conta uma vez"() {
        given:
        def body = evaluation()

        when:
        def decisions = (1..10).collect { post(body).json.decision }

        then:
        decisions.every { it == "APPROVE" }
    }

    def "contas diferentes nao se influenciam"() {
        given:
        def busy = UUID.randomUUID().toString()
        4.times { post(evaluation(sourceAccountId: busy)) }

        expect:
        post(evaluation()).json.decision == "APPROVE"
    }

    def "valida o pedido"() {
        expect:
        post(body).status == 400

        where:
        body << [
            [amountMinor: 1000],
            [transferId: "t", sourceAccountId: "a", amountMinor: 0, sourceCountry: "PT", targetCountry: "PT", sourceAgeDays: 1]
        ]
    }

    def "nao arranca com regras semanticamente invalidas"() {
        when:
        FraudApp.start('RULE r WHEN amont > 1 THEN BLOCK', new InMemoryVelocityStore(), Clock.systemUTC(), 0)

        then:
        def e = thrown(InvalidRulesException)
        e.message.contains("UNKNOWN_FIELD")
        e.message.contains("1:13")
    }

    def "nao arranca com erros de sintaxe"() {
        when:
        FraudApp.start('RULE r WHEN amount > THEN BLOCK', new InMemoryVelocityStore(), Clock.systemUTC(), 0)

        then:
        def e = thrown(InvalidRulesException)
        e.message.contains("Expected")
    }
}
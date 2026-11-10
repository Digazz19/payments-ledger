package ledger.http

import spock.lang.Timeout

class TransferRaceSpec extends ApiSpec {
    @Timeout(60)
    def "nunca gasta mais do que o saldo"() {
        given:
        def a = newAccount(balance: 100)
        def b = newAccount()

        when:
        def responses = concurrently(200) { i ->
            client.post("/transfers", transfer(a, b, 1))
        }
        println "Respostas por estado: ${responses.countBy { it.status }}"

        then:
        responses.every { it.status in [201, 422] }
        responses.count { it.status == 201 } == 100
        responses.count { it.status == 422 } == 100
        balance(a) == 0
        balance(b) == 100
    }
}
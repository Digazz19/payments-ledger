package ledger.http

import spock.lang.IgnoreIf
import spock.lang.Timeout

@IgnoreIf({ !env.BENCHMARK })
class ContentionBenchmarkSpec extends ApiSpec {
    @Timeout(180)
    def "conta muito disputada"() {
        given:
        def source = newAccount(balance: 1_000_000)
        def targets = (1..20).collect { newAccount() }

        when:
        def started = System.nanoTime()
        def responses = concurrently(1000) { i ->
            client.post("/transfers", transfer(source, targets[i % 20], 1))
        }
        def ms = (System.nanoTime() - started) / 1_000_000
        println "Conta disputada: ${ms} ms, ${responses.countBy { it.status }}"

        then:
        responses.every { it.status == 201 }
    }

    @Timeout(180)
    def "contas independentes"() {
        given:
        def pairs = (1..100).collect { [newAccount(balance: 1000), newAccount()] }

        when:
        def started = System.nanoTime()
        def responses = concurrently(1000) { i ->
            def pair = pairs[i % 100]
            client.post("/transfers", transfer(pair[0], pair[1], 1))
        }
        def ms = (System.nanoTime() - started) / 1_000_000
        println "Contas independentes: ${ms} ms, ${responses.countBy { it.status }}"

        then:
        responses.every { it.status == 201 }
    }
}
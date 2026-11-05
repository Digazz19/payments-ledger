package ledger.domain

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import spock.lang.Specification
import spock.lang.Timeout

class InMemoryLedgerConcurrencySpec extends Specification {
    @Timeout(30)
    def "transferencias opostas nao perdem dinheiro nem bloqueiam"() {
        given:
        def ledger = new InMemoryLedger()
        def a = ledger.createAccount()
        def b = ledger.createAccount()
        ledger.deposit(a, 10_000)
        ledger.deposit(b, 10_000)
        def pool = Executors.newVirtualThreadPerTaskExecutor()

        when:
        (1..2000).each { i ->
            def task = {
                i % 2 == 0 ? ledger.transfer(a, b, 1) : ledger.transfer(b, a, 1)
            } as Runnable
            pool.submit(task)
        }
        pool.shutdown()
        def finished = pool.awaitTermination(25, TimeUnit.SECONDS)

        then:
        finished
        ledger.balance(a) == 10_000
        ledger.balance(b) == 10_000
    }
}
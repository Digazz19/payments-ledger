package ledger.http

import spock.lang.Timeout

class RandomTrafficSpec extends ApiSpec {
    @Timeout(180)
    def "o ledger mantem-se correto com a seed #seed"() {
        given:
        println "Seed: $seed"
        def random = new Random(seed)
        def accounts = (1..20).collect { newAccount(balance: 1000 * it) }
        def total = accounts.sum { balance(it) }
        def ops = generateOps(random, accounts, 2000)

        when:
        def responses = concurrently(ops.size()) { i ->
            client.post("/transfers", ops[i].body, ["Idempotency-Key": ops[i].key])
        }
        def results = [ops, responses].transpose()
        def completed = results.findAll { op, r -> r.status == 201 }
        println "Respostas por estado: ${responses.countBy { it.status }}"

        then: "so ha respostas esperadas"
        responses.every { it.status in [201, 409, 422] }

        and: "nao se cria nem se perde dinheiro"
        accounts.sum { balance(it) } == total
        accounts.every { balance(it) >= 0 }

        and: "cada chave moveu dinheiro no maximo uma vez"
        completed.groupBy { op, r -> op.key }.every { key, pairs ->
            pairs.collect { op, r -> r.json.id }.toSet().size() == 1
        }

        and: "a base de dados tem uma transferencia por chave concluida"
        completedTransfersFrom(accounts) == completed.collect { op, r -> op.key }.toSet().size()

        and: "nenhuma chave fica presa em IN_PROGRESS"
        keysInProgress(ops) == 0

        where:
        seed << [1L, 42L, 2026L]
    }

    List<Map> generateOps(Random random, List<String> accounts, int count) {
        def ops = []
        count.times {
            if (ops && random.nextInt(100) < 20) {
                ops << ops[random.nextInt(ops.size())]
            } else {
                def source = accounts[random.nextInt(accounts.size())]
                def target = source
                while (target == source) {
                    target = accounts[random.nextInt(accounts.size())]
                }
                ops << [
                    key: new UUID(random.nextLong(), random.nextLong()).toString(),
                    body: transfer(source, target, 1 + random.nextInt(500))
                ]
            }
        }
        ops
    }

    long completedTransfersFrom(List<String> accounts) {
        sql.firstRow(
            "SELECT count(*) AS c FROM transfers WHERE status = 'COMPLETED' AND source_account_id = ANY(?::uuid[])",
            ["{" + accounts.join(",") + "}"]
        ).c
    }

    long keysInProgress(List<Map> ops) {
        sql.firstRow(
            "SELECT count(*) AS c FROM idempotency_keys WHERE state = 'IN_PROGRESS' AND idempotency_key = ANY(?::text[])",
            ["{" + ops*.key.toSet().join(",") + "}"]
        ).c
    }
}
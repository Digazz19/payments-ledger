package ledger.http

import ledger.idempotency.IdempotencyRepository
import ledger.idempotency.StoredResponse
import spock.lang.Timeout

class StaleKeySpec extends ApiSpec {
    String newKey() {
        UUID.randomUUID().toString()
    }

    void insertKey(String key, String hash, String age) {
        sql.execute("""
            INSERT INTO idempotency_keys (idempotency_key, request_hash, state, lock_token, locked_at)
            VALUES ($key, $hash, 'IN_PROGRESS', gen_random_uuid(), now() - CAST($age AS interval))
        """)
    }

    def "retoma uma chave abandonada"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()
        def body = transfer(a, b, 100)
        def key = newKey()
        insertKey(key, client.hashOf("/transfers", body), "5 minutes")

        when:
        def first = client.post("/transfers", body, ["Idempotency-Key": key])
        def replay = client.post("/transfers", body, ["Idempotency-Key": key])

        then:
        first.status == 201
        replay.json == first.json
        balance(a) == 900
    }

    def "nao toca numa chave recente"() {
        given:
        def body = transfer(newAccount(balance: 1000), newAccount(), 100)
        def key = newKey()
        insertKey(key, client.hashOf("/transfers", body), "1 second")

        when:
        def r = client.post("/transfers", body, ["Idempotency-Key": key])

        then:
        r.status == 409
        r.json.code == "IDEMPOTENCY_IN_PROGRESS"
    }

    def "nao retoma uma chave abandonada com outro corpo"() {
        given:
        def a = newAccount(balance: 1000)
        def key = newKey()
        insertKey(key, client.hashOf("/transfers", transfer(a, newAccount(), 100)), "5 minutes")

        when:
        def r = client.post("/transfers", transfer(a, newAccount(), 200), ["Idempotency-Key": key])

        then:
        r.status == 422
        balance(a) == 1000
    }

    @Timeout(60)
    def "so um pedido retoma uma chave abandonada"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()
        def body = transfer(a, b, 100)
        def key = newKey()
        insertKey(key, client.hashOf("/transfers", body), "5 minutes")

        when:
        def responses = concurrently(20) { i ->
            client.post("/transfers", body, ["Idempotency-Key": key])
        }

        then:
        responses.every { it.status in [201, 409] }
        balance(a) == 900
    }

    def "o dono antigo nao consegue concluir depois de perder a chave"() {
        given:
        def repository = new IdempotencyRepository(database.dsl())
        def key = newKey()
        def oldToken = UUID.randomUUID()
        def newToken = UUID.randomUUID()
        repository.tryReserve(key, "hash", oldToken)
        sql.execute("UPDATE idempotency_keys SET locked_at = now() - interval '5 minutes' WHERE idempotency_key = $key")

        when:
        def tookOver = repository.tryTakeOver(key, "hash", newToken, java.time.Duration.ofSeconds(60))
        repository.complete(database.dsl(), key, oldToken, new StoredResponse(201, "{}"))

        then:
        tookOver
        thrown(IllegalStateException)
    }
}
package ledger.http

class TransfersApiSpec extends ApiSpec {
    def "transfere e cria duas entradas"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()

        when:
        def r = client.post("/transfers", transfer(a, b, 300))

        then:
        r.status == 201
        r.json.status == "COMPLETED"
        balance(a) == 700
        balance(b) == 300
        client.get("/transfers/${r.json.id}").json.amountMinor == 300
    }

    def "regista a recusa sem mover dinheiro"() {
        given:
        def a = newAccount(balance: 100)
        def b = newAccount()

        when:
        def r = client.post("/transfers", transfer(a, b, 101))

        then:
        r.status == 422
        r.json.status == "REJECTED"
        r.json.reason == "INSUFFICIENT_FUNDS"
        balance(a) == 100
        sql.firstRow(
            "SELECT count(*) AS c FROM ledger_entries WHERE transfer_id = ?::uuid",
            [r.json.id]
        ).c == 0
    }

    def "valida o pedido"() {
        given:
        def a = newAccount(balance: 100)
        def b = newAccount()

        expect:
        client.post("/transfers", transfer(a, a, 10)).status == 400
        client.post("/transfers", transfer(a, b, 0)).status == 400
        client.post("/transfers", transfer(a, b, 10) + [currency: "USD"]).status == 400
    }

    def "devolve 404 quando uma conta nao existe"() {
        when:
        def r = client.post("/transfers", transfer(newAccount(balance: 100), UUID.randomUUID().toString(), 10))

        then:
        r.status == 404
        r.json.code == "ACCOUNT_NOT_FOUND"
    }

    def "devolve 404 para uma transferencia inexistente"() {
        expect:
        client.get("/transfers/${UUID.randomUUID()}").status == 404
    }

    def "desfaz tudo se uma escrita falhar"() {
        given:
        def a = newAccount(balance: 1000)
        def b = newAccount()
        failLedgerWrites()

        when:
        def r = client.post("/transfers", transfer(a, b, 300))

        then:
        r.status == 500
        r.json.code == "INTERNAL_ERROR"
        balance(a) == 1000
        balance(b) == 0

        cleanup:
        restoreLedgerWrites()
    }
}
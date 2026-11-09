package ledger.http

class DepositsApiSpec extends ApiSpec {
    def "deposito atualiza saldo e cria duas entradas"() {
        given:
        def id = newAccount()

        when:
        def r = client.post("/deposits", [accountId: id, amountMinor: 1000])

        then:
        r.status == 201
        r.json.status == "COMPLETED"
        balance(id) == 1000
        sql.firstRow(
            "SELECT count(*) AS c FROM ledger_entries WHERE transfer_id = ?::uuid",
            [r.json.id]
        ).c == 2
    }

    def "a conta de sistema fica com o valor negativo"() {
        given:
        def before = sql.firstRow(
            "SELECT balance_minor FROM account_balances WHERE account_id = ?::uuid",
            [SYSTEM_ACCOUNT_ID]
        ).balance_minor

        when:
        client.post("/deposits", [accountId: newAccount(), amountMinor: 500])

        then:
        sql.firstRow(
            "SELECT balance_minor FROM account_balances WHERE account_id = ?::uuid",
            [SYSTEM_ACCOUNT_ID]
        ).balance_minor == before - 500
    }

    def "recusa valor invalido"() {
        expect:
        client.post("/deposits", [accountId: newAccount(), amountMinor: amount]).status == 400

        where:
        amount << [0, -1, null]
    }

    def "devolve 404 para uma conta inexistente"() {
        when:
        def r = client.post("/deposits", [accountId: UUID.randomUUID().toString(), amountMinor: 100])

        then:
        r.status == 404
        r.json.code == "ACCOUNT_NOT_FOUND"
    }
}
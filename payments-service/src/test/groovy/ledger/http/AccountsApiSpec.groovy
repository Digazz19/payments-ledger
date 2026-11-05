package ledger.http

class AccountsApiSpec extends ApiSpec {
    def "responde ao health check"() {
        expect:
        client.get("/health").status == 200
    }

    def "cria e le uma conta"() {
        when:
        def created = client.post("/accounts", [ownerName: "Ana", currency: "EUR", country: "PT"])

        then:
        created.status == 201
        created.json.balanceMinor == 0

        when:
        def read = client.get("/accounts/${created.json.id}")

        then:
        read.status == 200
        read.json.ownerName == "Ana"
    }

    def "valida o pedido"() {
        when:
        def response = client.post("/accounts", body)

        then:
        response.status == 400
        response.json.code == "VALIDATION_ERROR"

        where:
        body << [
            [currency: "EUR", country: "PT"],
            [ownerName: "Ana", currency: "USD", country: "PT"],
            [ownerName: "Ana", currency: "EUR", country: "Portugal"]
        ]
    }

    def "devolve 404 em JSON"() {
        when:
        def r = client.get("/accounts/${UUID.randomUUID()}")

        then:
        r.status == 404
        r.json.code == "ACCOUNT_NOT_FOUND"
    }

    def "recusa um id invalido"() {
        expect:
        client.get("/accounts/abc").status == 400
    }
}
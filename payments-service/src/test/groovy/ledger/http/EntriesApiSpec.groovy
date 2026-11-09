package ledger.http

class EntriesApiSpec extends ApiSpec {
    def "percorre tudo sem repetir entradas"() {
        given:
        def id = newAccount()
        5.times { client.post("/deposits", [accountId: id, amountMinor: 10]) }

        when:
        def first = client.get("/accounts/$id/entries?limit=2").json
        def second = client.get("/accounts/$id/entries?limit=2&cursor=${first.nextCursor}").json
        def third = client.get("/accounts/$id/entries?limit=2&cursor=${second.nextCursor}").json
        def ids = (first.items + second.items + third.items)*.id

        then:
        ids.size() == 5
        ids.toSet().size() == 5
        third.nextCursor == null
    }

    def "ordena da mais recente para a mais antiga"() {
        given:
        def id = newAccount()
        [10, 20, 30].each { client.post("/deposits", [accountId: id, amountMinor: it]) }

        when:
        def page = client.get("/accounts/$id/entries").json

        then:
        page.items*.amountMinor == [30, 20, 10]
        page.items.every { it.direction == "CREDIT" && it.createdAt }
        page.nextCursor == null
    }

    def "recusa um limit invalido"() {
        expect:
        client.get("/accounts/${newAccount()}/entries?limit=$limit").status == 400

        where:
        limit << ["0", "101", "abc"]
    }

    def "devolve 404 para uma conta inexistente"() {
        expect:
        client.get("/accounts/${UUID.randomUUID()}/entries").status == 404
    }
}
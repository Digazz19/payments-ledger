package ledger.db

import static ledger.jooq.Tables.ACCOUNTS

class DatabaseSpec extends PostgresSpec {
    def "le a conta de sistema com tipos gerados"() {
        given:
        def database = new Database(postgres.jdbcUrl, postgres.username, postgres.password)

        when:
        def count = database.dsl().fetchCount(ACCOUNTS, ACCOUNTS.TYPE.eq("SYSTEM"))

        then:
        count == 1

        cleanup:
        database.close()
    }
}
package ledger.db

import java.sql.SQLException

class SchemaSpec extends PostgresSpec {
    def "a conta de sistema existe e pode ficar negativa"() {
        expect:
        sql.firstRow(
            "SELECT allow_negative FROM account_balances WHERE account_id = ?::uuid",
            [SYSTEM_ACCOUNT_ID]
        ).allow_negative == true
    }

    def "um cliente nao pode ficar com saldo negativo"() {
        given:
        def id = insertCustomer(balance: 0)

        when:
        sql.executeUpdate(
            "UPDATE account_balances SET balance_minor = -1 WHERE account_id = ?::uuid",
            [id]
        )

        then:
        def e = thrown(SQLException)
        e.message.contains("balance_not_negative")
    }
}
package ledger.db

import groovy.sql.Sql
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import spock.lang.Shared
import spock.lang.Specification
import java.time.Duration

abstract class PostgresSpec extends Specification {
    static final String SYSTEM_ACCOUNT_ID = "00000000-0000-0000-0000-000000000001"

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
        .withStartupTimeout(Duration.ofMinutes(2))

    static {
        postgres.start()
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .connectRetries(10)
            .load()
            .migrate()
    }

    @Shared
    Sql sql

    def setupSpec() {
        sql = Sql.newInstance(postgres.jdbcUrl, postgres.username, postgres.password, "org.postgresql.Driver")
    }

    def cleanupSpec() {
        sql?.close()
    }

    String insertCustomer(Map args) {
        def id = UUID.randomUUID().toString()

        sql.execute("""
            INSERT INTO accounts (id, owner_name, currency, country, type)
            VALUES (?::uuid, 'Teste', 'EUR', 'PT', 'CUSTOMER')
        """, [id])

        sql.execute("""
            INSERT INTO account_balances (account_id, balance_minor)
            VALUES (?::uuid, ?)
        """, [id, args.balance])

        id
    }
}
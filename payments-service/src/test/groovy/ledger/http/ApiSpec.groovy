package ledger.http

import ledger.App
import ledger.db.Database
import ledger.db.PostgresSpec
import spock.lang.Shared

abstract class ApiSpec extends PostgresSpec {
    @Shared
    Database database

    @Shared
    App app

    @Shared
    TestClient client

    def setupSpec() {
        database = new Database(postgres.jdbcUrl, postgres.username, postgres.password)
        app = App.start(database, 0)
        client = new TestClient(app.port())
    }

    def cleanupSpec() {
        app?.stop()
        database?.close()
    }
}
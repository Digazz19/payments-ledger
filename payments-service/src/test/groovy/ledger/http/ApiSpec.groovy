package ledger.http

import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import ledger.App
import ledger.db.Database
import ledger.db.PostgresSpec
import ledger.fraud.HttpFraudClient
import ledger.reconciliation.Reconciliation
import spock.lang.Shared

abstract class ApiSpec extends PostgresSpec {
    @Shared
    Database database

    @Shared
    Reconciliation reconciliation

    @Shared
    FraudStub fraudStub

    @Shared
    App app

    @Shared
    TestClient client

    def setupSpec() {
        database = new Database(postgres.jdbcUrl, postgres.username, postgres.password)
        reconciliation = new Reconciliation(database.dsl())
        fraudStub = new FraudStub()
        app = App.start(database, new HttpFraudClient(fraudStub.url(), fraudTimeout()), 0)
        client = new TestClient(app.port())
    }

    def cleanupSpec() {
        app?.stop()
        fraudStub?.stop()
        database?.close()
    }

    def setup() {
        fraudStub.reset()
    }

    def cleanup() {
        assertLedgerConsistent()
    }

    void assertLedgerConsistent() {
        assert reconciliation.check().isEmpty()
        assert reconciliation.ledgerTotal() == 0
    }

    String newAccount(Map args = [:]) {
        def country = args.country ?: "PT"
        def id = client.post("/accounts", [ownerName: "Teste", currency: "EUR", country: country]).json.id
        if (args.balance) {
            client.post("/deposits", [accountId: id, amountMinor: args.balance])
        }
        id
    }

    long balance(String id) {
        client.get("/accounts/$id").json.balanceMinor
    }

    Map transfer(String source, String target, long amount) {
        [sourceAccountId: source, targetAccountId: target, amountMinor: amount, currency: "EUR"]
    }

    Duration fraudTimeout() {
        Duration.ofSeconds(5)
    }

    void failLedgerWrites() {
        sql.execute('''
            CREATE OR REPLACE FUNCTION fail_ledger_write() RETURNS trigger AS $$
            BEGIN
                RAISE EXCEPTION 'ledger writes disabled for test';
            END;
            $$ LANGUAGE plpgsql
        ''')
        sql.execute('''
            CREATE TRIGGER fail_ledger_write
            BEFORE INSERT ON ledger_entries
            FOR EACH ROW EXECUTE FUNCTION fail_ledger_write()
        ''')
    }

    void restoreLedgerWrites() {
        sql.execute('DROP TRIGGER IF EXISTS fail_ledger_write ON ledger_entries')
    }

    List<TestClient.Response> concurrently(int n, Closure<TestClient.Response> action) {
        def pool = Executors.newFixedThreadPool(Math.min(n, 50))
        def start = new CountDownLatch(1)
        try {
            def futures = (0..<n).collect { i ->
                pool.submit({
                    start.await()
                    action(i)
                } as Callable)
            }
            start.countDown()
            futures*.get()
        } finally {
            pool.shutdown()
        }
    }
}
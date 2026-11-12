package ledger;

import java.time.Duration;
import ledger.db.Database;
import ledger.fraud.FraudClient;
import ledger.fraud.HttpFraudClient;

import org.flywaydb.core.Flyway;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        String url = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/ledger");
        String user = env("DATABASE_USER", "ledger");
        String password = env("DATABASE_PASSWORD", "ledger");
        int port = Integer.parseInt(env("PORT", "8080"));

        Flyway.configure()
            .dataSource(url, user, password)
            .load()
            .migrate();

        Database database = new Database(url, user, password);
        String fraudUrl = env("FRAUD_URL", "http://localhost:8081");
        FraudClient fraud = new HttpFraudClient(fraudUrl, Duration.ofMillis(300));

        App app = App.start(database, fraud, port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            database.close();
        }));

        System.out.println("payments-service running on port " + app.port());
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
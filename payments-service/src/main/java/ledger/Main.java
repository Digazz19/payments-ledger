package ledger;

import ledger.db.Database;
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
        App app = App.start(database, port);

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
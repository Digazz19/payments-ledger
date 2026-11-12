package fraud.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String rulesPath = env("RULES_PATH", "rules/default.rules");
        int port = Integer.parseInt(env("PORT", "8081"));

        String rules = Files.readString(Path.of(rulesPath));
        FraudApp app = FraudApp.start(rules, new InMemoryVelocityStore(), Clock.systemUTC(), port);

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
        System.out.println("fraud-service running on port " + app.port() + " with rules from " + rulesPath);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
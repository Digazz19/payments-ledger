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
        String redisHost = env("REDIS_HOST", "localhost");
        int redisPort = Integer.parseInt(env("REDIS_PORT", "6379"));
        RedisVelocityStore velocity = new RedisVelocityStore(redisHost, redisPort);

        FraudApp app;
        try {
            app = FraudApp.start(rules, velocity, Clock.systemUTC(), port);
        } catch (RuntimeException error) {
            velocity.close();
            throw error;
        }

        System.out.println("fraud-service running on port " + app.port() + " with rules from " + rulesPath);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
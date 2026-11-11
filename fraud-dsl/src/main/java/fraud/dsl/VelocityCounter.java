package fraud.dsl;

import java.time.Duration;

@FunctionalInterface
public interface VelocityCounter {
    long count(Duration window);
}
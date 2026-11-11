package fraud.dsl;

import java.time.Duration;
import java.util.List;

public sealed interface Operand {

    record FieldRef(List<String> path, Position position) implements Operand {
    }

    record Velocity(Duration window, Position position) implements Operand {
    }

    record NumberLit(long value) implements Operand {
    }

    record StringLit(String value) implements Operand {
    }
}
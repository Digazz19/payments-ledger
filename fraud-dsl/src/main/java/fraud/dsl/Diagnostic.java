package fraud.dsl;

public record Diagnostic(String code, String message, Position position) {

    @Override
    public String toString() {
        return "%d:%d %s: %s".formatted(position.line(), position.column(), code, message);
    }
}
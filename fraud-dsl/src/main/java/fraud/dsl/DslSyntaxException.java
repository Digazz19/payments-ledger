package fraud.dsl;

public class DslSyntaxException extends RuntimeException {

    private final int line;
    private final int column;

    public DslSyntaxException(String message, int line, int column) {
        super("%s at %d:%d".formatted(message, line, column));
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}